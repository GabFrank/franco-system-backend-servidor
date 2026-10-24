package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.AjusteStockLoteResultadoDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.ReasignarLoteInput;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * REASIGNAR (lotes fase 3, entrega 2): pasar unidades del lote A al lote B en una sucursal, sin
 * cambiar la existencia del producto.
 *
 * Escribe un movimiento AJUSTE en cero con dos hijas (−cantidad en A, +cantidad en B), igual que
 * ATRIBUIR/LIBERAR, bajo el lock de saldo de la sucursal. Las filas nacen en el central (ids
 * impares) y bajan a la filial por la publicación filtrada: no se escribe nada en la filial.
 *
 * La filial vende de A con su saldo local y sube con retraso, así que antes de escribir:
 * - los dos lotes tienen que existir YA en la filial (si no, las hijas bajarían huérfanas);
 * - la replicación con esa filial tiene que estar al día (≤ {@link #ATRASO_MAXIMO_SEGUNDOS});
 * - no se vacía A si tuvo ventas en esa sucursal en los últimos {@link #MINUTOS_VENTAS_RECIENTES}.
 * Los dos últimos avisos se pueden saltear con {@code forzar}; el primero no.
 * Si aun así A queda negativo por una venta en vuelo, la venta no se bloquea (regla de FEFO) y el
 * negativo aparece en el saldo por lote para corregirlo con el conteo rápido.
 */
@Log
@Service
public class ReasignacionLoteService {

    static final double EPSILON = 0.0001;
    static final double ATRASO_MAXIMO_SEGUNDOS = 300;
    static final int MINUTOS_VENTAS_RECIENTES = 10;

    @Autowired private MovimientoStockService movimientoStockService;
    @Autowired private MovimientoStockLoteService movimientoStockLoteService;
    @Autowired private LoteService loteService;
    @Autowired private LoteFefoService loteFefoService;
    @Autowired private ProductoService productoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private ModificacionService modificacionService;
    @Autowired private SincronizacionFilialService sincronizacionFilialService;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    @Transactional
    public AjusteStockLoteResultadoDto reasignar(ReasignarLoteInput input) {
        validar(input);
        loteFefoService.bloquearSaldo(input.getSucursalId());

        Producto producto = productoService.findById(input.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto " + input.getProductoId()));
        if (!Boolean.TRUE.equals(producto.getLote())) {
            throw new IllegalArgumentException("El producto '" + producto.getDescripcion()
                    + "' no tiene control de lote.");
        }
        Lote origen = lote(input.getLoteOrigenId(), producto);
        Lote destino = lote(input.getLoteDestinoId(), producto);

        double saldoOrigen = saldo(input.getProductoId(), input.getSucursalId(), origen.getId());
        if (input.getSaldoEsperado() != null && Math.abs(input.getSaldoEsperado() - saldoOrigen) > EPSILON) {
            throw new IllegalArgumentException("El saldo del lote " + origen.getNumeroLote()
                    + " cambió (ahora " + saldoOrigen + "): recargá la pantalla.");
        }
        double cantidad = input.getCantidad();
        if (cantidad - saldoOrigen > EPSILON) {
            throw new IllegalArgumentException("El lote " + origen.getNumeroLote() + " tiene " + saldoOrigen
                    + " en esta sucursal: no se pueden pasar " + cantidad + ".");
        }

        verificarFilial(input, origen, destino, saldoOrigen);

        Usuario usuario = input.getUsuarioId() != null
                ? usuarioService.findById(input.getUsuarioId()).orElse(null) : null;
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setSucursalId(input.getSucursalId());
        movimiento.setProducto(producto);
        movimiento.setTipoMovimiento(TipoMovimiento.AJUSTE);
        movimiento.setReferencia(producto.getId());
        movimiento.setCantidad(0.0);
        movimiento.setEstado(true);
        movimiento.setUsuario(usuario);
        movimiento.setCreadoEn(LocalDateTime.now());
        movimiento = movimientoStockService.save(movimiento);

        movimientoStockLoteService.save(fila(movimiento, producto, origen, -cantidad, usuario));
        movimientoStockLoteService.save(fila(movimiento, producto, destino, cantidad, usuario));
        auditar(movimiento, origen, destino, input);

        return new AjusteStockLoteResultadoDto(movimiento.getId(), movimiento.getSucursalId(),
                destino.getId(), destino.getNumeroLote(), 0.0,
                saldo(input.getProductoId(), input.getSucursalId(), destino.getId()), null);
    }

    void validar(ReasignarLoteInput input) {
        if (input == null || input.getProductoId() == null || input.getSucursalId() == null) {
            throw new IllegalArgumentException("El producto y la sucursal son obligatorios.");
        }
        if (input.getLoteOrigenId() == null || input.getLoteDestinoId() == null) {
            throw new IllegalArgumentException("Hay que elegir el lote de origen y el de destino.");
        }
        if (input.getLoteOrigenId().equals(input.getLoteDestinoId())) {
            throw new IllegalArgumentException("El lote de origen y el de destino son el mismo.");
        }
        if (input.getCantidad() == null || input.getCantidad() <= EPSILON) {
            throw new IllegalArgumentException("La cantidad a pasar tiene que ser mayor a cero.");
        }
        if (input.getMotivo() == null || input.getMotivo().trim().isEmpty()) {
            throw new IllegalArgumentException("Para pasar unidades a otro lote el motivo es obligatorio.");
        }
    }

    private void verificarFilial(ReasignarLoteInput input, Lote origen, Lote destino, double saldoOrigen) {
        boolean forzar = Boolean.TRUE.equals(input.getForzar());
        Long presentes = sincronizacionFilialService.lotesPresentesEnFilial(input.getSucursalId(),
                Arrays.asList(origen.getId(), destino.getId()));
        if (presentes == null) {
            throw new IllegalArgumentException("No se pudo consultar la filial de esta sucursal: no se puede "
                    + "confirmar que tenga los dos lotes. Reintentá cuando esté en línea.");
        }
        if (presentes < 2) {
            throw new IllegalArgumentException("La filial de esta sucursal todavía no tiene alguno de los dos "
                    + "lotes. Esperá a que bajen (o corré la bajada de lotes viejos) y reintentá.");
        }
        if (forzar) {
            return;
        }
        Double atraso = sincronizacionFilialService.atrasoSegundos(input.getSucursalId());
        if (atraso == null || atraso > ATRASO_MAXIMO_SEGUNDOS) {
            throw new IllegalArgumentException("La sucursal no está sincronizada con el central"
                    + (atraso != null && atraso < 1e8 ? " (atraso " + Math.round(atraso / 60) + " min)" : "")
                    + ": reintentá en unos minutos.");
        }
        if (Math.abs(input.getCantidad() - saldoOrigen) <= EPSILON && ventasRecientes(input, origen) > 0) {
            throw new IllegalArgumentException("El lote " + origen.getNumeroLote() + " tuvo ventas en los últimos "
                    + MINUTOS_VENTAS_RECIENTES + " minutos en esta sucursal: vaciarlo ahora puede dejarlo "
                    + "negativo. Reintentá más tarde o forzalo.");
        }
    }

    private long ventasRecientes(ReasignarLoteInput input, Lote origen) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM operaciones.movimiento_stock_lote " +
                "WHERE lote_id = :l AND sucursal_id = :s AND cantidad < 0 AND estado = true AND creado_en >= :f",
                new MapSqlParameterSource("l", origen.getId()).addValue("s", input.getSucursalId())
                        .addValue("f", Timestamp.valueOf(LocalDateTime.now().minusMinutes(MINUTOS_VENTAS_RECIENTES))),
                Long.class);
        return n != null ? n : 0L;
    }

    private Lote lote(Long id, Producto producto) {
        Lote l = loteService.findById(id).orElseThrow(() -> new IllegalArgumentException("No existe el lote " + id));
        if (l.getProducto() == null || !producto.getId().equals(l.getProducto().getId())) {
            throw new IllegalArgumentException("El lote " + l.getNumeroLote() + " es de otro producto.");
        }
        return l;
    }

    private double saldo(Long productoId, Long sucursalId, Long loteId) {
        for (StockLoteDto s : movimientoStockLoteService.stockPorLote(productoId, sucursalId)) {
            if (loteId.equals(s.getLoteId())) {
                return s.getCantidadDisponible() != null ? s.getCantidadDisponible() : 0.0;
            }
        }
        return 0.0;
    }

    private MovimientoStockLote fila(MovimientoStock m, Producto p, Lote lote, double cantidad, Usuario u) {
        MovimientoStockLote f = new MovimientoStockLote();
        f.setSucursalId(m.getSucursalId());
        f.setMovimientoStockId(m.getId());
        f.setLote(lote);
        f.setProducto(p);
        f.setNumeroLote(lote.getNumeroLote());
        f.setCantidad(cantidad);
        f.setReferencia(p.getId());
        f.setEstado(true);
        f.setUsuario(u);
        f.setCreadoEn(m.getCreadoEn());
        return f;
    }

    private void auditar(MovimientoStock m, Lote origen, Lote destino, ReasignarLoteInput in) {
        try {
            String detalle = String.format("REASIGNAR | %s -> %s | cantidad %s | referencia %s | motivo: %s",
                    origen.getNumeroLote(), destino.getNumeroLote(), in.getCantidad(),
                    in.getReferencia() != null ? in.getReferencia() : "-", in.getMotivo().trim());
            modificacionService.registrarInsercion(m, "AJUSTE_STOCK_LOTE", "operaciones",
                    "movimiento_stock_lote", detalle);
        } catch (Exception e) {
            log.warning("Error registrando auditoría de la reasignación: " + e.getMessage());
        }
    }
}
