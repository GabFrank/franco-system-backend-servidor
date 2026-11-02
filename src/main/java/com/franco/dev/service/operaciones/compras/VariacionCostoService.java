package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.compras.VariacionCostoPedidoItem;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoService;
import graphql.GraphQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ítems del pedido en el desktop: el precio contra la última compra (para pintar lo que subió o bajó) y el
 * vencimiento cargado desde la tabla.
 */
@Service
public class VariacionCostoService {

    /** Por debajo de este cambio el precio se considera igual (redondeos de la factura). */
    static final double UMBRAL = 0.01;

    @Autowired private ComprasSecurityService seguridad;
    @Autowired private PedidoService pedidoService;
    @Autowired private PedidoItemService pedidoItemService;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    /**
     * Un elemento por ítem del pedido. La última compra es la última nota de otro pedido con el producto, en la
     * misma moneda, sin bonificaciones ni rechazos; los dos precios son por unidad base y con IVA, como se cargan.
     */
    @Transactional(readOnly = true)
    public List<VariacionCostoPedidoItem> variaciones(Long pedidoId) {
        seguridad.exigirCompras();
        Pedido pedido = pedidoService.findById(pedidoId)
                .orElseThrow(() -> new GraphQLException("No existe el pedido " + pedidoId + "."));
        List<PedidoItem> items = pedidoItemService.findByPedidoId(pedidoId);
        Set<Long> productos = items.stream().filter(i -> i.getProducto() != null)
                .map(i -> i.getProducto().getId()).collect(Collectors.toSet());
        Map<Long, Double> ultimos = new HashMap<>();
        if (!productos.isEmpty() && pedido.getMoneda() != null) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("productos", productos)
                    .addValue("pedido", pedidoId)
                    .addValue("moneda", pedido.getMoneda().getId());
            jdbc.query("SELECT DISTINCT ON (nri.producto_id) nri.producto_id, nri.precio_unitario_en_nota "
                    + "FROM operaciones.nota_recepcion_item nri "
                    + "JOIN operaciones.nota_recepcion nr ON nr.id = nri.nota_recepcion_id "
                    + "WHERE nri.producto_id IN (:productos) AND nr.pedido_id <> :pedido AND nr.moneda_id = :moneda "
                    + "AND COALESCE(nri.es_bonificacion, false) = false AND COALESCE(nr.es_nota_rechazo, false) = false "
                    + "AND COALESCE(nri.estado::text, '') <> 'RECHAZADO' AND nri.precio_unitario_en_nota > 0 "
                    + "ORDER BY nri.producto_id, nri.id DESC", params,
                    rs -> {
                        ultimos.put(rs.getLong(1), rs.getDouble(2));
                    });
        }
        List<VariacionCostoPedidoItem> resultado = new ArrayList<>();
        for (PedidoItem item : items) {
            Double ultimo = item.getProducto() != null ? ultimos.get(item.getProducto().getId()) : null;
            resultado.add(variacion(item.getId(), item.getPrecioUnitarioSolicitado(), ultimo,
                    Boolean.TRUE.equals(item.getEsBonificacion())));
        }
        return resultado;
    }

    static VariacionCostoPedidoItem variacion(Long pedidoItemId, Double actual, Double ultimo, boolean bonificacion) {
        VariacionCostoPedidoItem v = new VariacionCostoPedidoItem();
        v.setPedidoItemId(pedidoItemId);
        v.setPrecioActual(actual);
        v.setUltimoPrecio(ultimo);
        if (bonificacion || actual == null || actual <= 0 || ultimo == null || ultimo <= 0) {
            v.setDireccion("SIN_HISTORIAL");
            return v;
        }
        double variacion = (actual - ultimo) / ultimo;
        v.setVariacion(variacion);
        if (Math.abs(variacion) < UMBRAL) {
            v.setDireccion("IGUAL");
            return v;
        }
        v.setDireccion(variacion > 0 ? "SUBIO" : "BAJO");
        v.setTexto((variacion > 0 ? "Subió" : "Bajó") + " contra la última compra: " + CruceFactura.importe(ultimo)
                + " → " + CruceFactura.importe(actual) + " (" + (variacion > 0 ? "+" : "−")
                + CruceFactura.cantidad(CruceFactura.redondear(Math.abs(variacion) * 100, 1)) + " %)");
        return v;
    }

    /** El vencimiento esperado de un ítem, desde la tabla del desktop. {@code null} o vacío lo borra. */
    @Transactional
    public PedidoItem actualizarVencimiento(Long pedidoItemId, String vencimiento) {
        seguridad.exigirCompras();
        PedidoItem item = pedidoItemService.findById(pedidoItemId)
                .orElseThrow(() -> new GraphQLException("No existe el ítem " + pedidoItemId + "."));
        if (vencimiento == null || vencimiento.trim().isEmpty()) {
            item.setVencimientoEsperado(null);
        } else {
            try {
                item.setVencimientoEsperado(LocalDate.parse(vencimiento.trim().substring(0, 10)).atStartOfDay());
            } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
                throw new GraphQLException("El vencimiento tiene que ser una fecha (aaaa-mm-dd).");
            }
        }
        return pedidoItemService.save(item);
    }
}
