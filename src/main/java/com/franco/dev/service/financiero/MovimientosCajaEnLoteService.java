package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualVinculo;
import graphql.GraphQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Movimientos y transferencias de caja mayor en varias monedas, en un solo pedido (issue #376).
 *
 * <p>Los diálogos de la caja mayor dejan cargar guaraníes, reales y dólares a la vez, y el desktop
 * mandaba un pedido por moneda: si la segunda se rechazaba la primera ya había entrado, y la operación
 * quedaba a medias. Acá el lote entra entero o no entra: una transacción por pedido.</p>
 *
 * <p>Orden, siempre el mismo: validar el pedido → clave de idempotencia → permiso sobre las cajas →
 * <b>todos</b> los saldos del lote con lock, por (caja, moneda) ascendente → recién ahí registrar. Los
 * saldos se toman antes y juntos porque registrar de a una moneda se cruza con el resto del módulo, que
 * ordena por caja, y porque cada {@code registrar} escribe además la fila de la caja (el shim
 * {@code saldo_gs/rs/ds}): tomándolos antes, esa fila se escribe siempre después de tener los saldos,
 * igual que en un movimiento de una sola moneda.</p>
 *
 * <p>Cada moneda queda como un movimiento —o un par de patas vinculadas— independiente: se anulan por
 * separado, como siempre.</p>
 */
@Service
@RequiredArgsConstructor
public class MovimientosCajaEnLoteService {

    static final String OPERACION_MOVIMIENTOS = "MOVIMIENTOS_CAJA_LOTE";
    static final String OPERACION_TRANSFERENCIAS = "TRANSFERENCIAS_CAJA_LOTE";

    static final int MAXIMO_DE_MONTOS = 10;
    /** {@code caja_virtual_saldo.saldo} es numeric(18,4): 14 enteros y 4 decimales. */
    static final int DECIMALES = 4;
    static final BigDecimal TOPE = new BigDecimal("100000000000000");

    private final TesoreriaService tesoreriaService;
    private final TesoreriaSecurityService seguridad;
    private final IdempotenciaService idempotenciaService;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final CajaVirtualSaldoRepository saldoRepository;
    private final MonedaRepository monedaRepository;
    private final MovimientoCajaVirtualRepository movimientoRepository;

    /** Un monto del pedido: moneda y cantidad, como llegan. */
    @lombok.Value
    public static class Monto {
        Long monedaId;
        Double cantidad;
    }

    /**
     * Registra un ingreso, un egreso o un ajuste en una o más monedas. Todo o nada.
     *
     * @param claveIdempotencia la del intento del usuario: el mismo pedido repetido no registra de nuevo
     */
    @Transactional
    public Boolean registrarMovimientos(Long cajaVirtualId, CajaVirtualTipoMovimiento tipo, List<Monto> montos,
                                        String descripcion, Usuario usuario, String claveIdempotencia) {
        exigirUsuario(usuario);
        if (cajaVirtualId == null) throw new GraphQLException("Debe indicar la caja.");
        if (tipo != CajaVirtualTipoMovimiento.INGRESO && tipo != CajaVirtualTipoMovimiento.EGRESO
                && tipo != CajaVirtualTipoMovimiento.AJUSTE) {
            throw new GraphQLException("Acá solo se registran ingresos, egresos y ajustes.");
        }
        // El ajuste lleva signo; en ingreso y egreso el sentido lo da el tipo.
        Map<Long, BigDecimal> porMoneda = validar(montos, tipo == CajaVirtualTipoMovimiento.AJUSTE);

        HuellaPedido huella = new HuellaPedido().texto(tipo.name()).id(cajaVirtualId);
        porMoneda.forEach((monedaId, cantidad) -> huella.id(monedaId).numero(cantidad));
        huella.texto(descripcion);

        idempotenciaService.ejecutar(claveIdempotencia, OPERACION_MOVIMIENTOS, huella.calcular(), usuario,
                () -> {
                    Map<Long, Moneda> monedas = monedas(porMoneda);
                    seguridad.requireEscrituraCaja(cajaVirtualId);
                    CajaVirtual caja = cajaVirtualRepository.findById(cajaVirtualId)
                            .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + cajaVirtualId));
                    tomarSaldos(java.util.Collections.singletonList(cajaVirtualId), porMoneda);

                    Long primero = null;
                    for (Map.Entry<Long, BigDecimal> e : porMoneda.entrySet()) {
                        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
                        m.setCajaVirtual(caja);
                        m.setTipoMovimiento(tipo);
                        m.setCantidad(e.getValue().doubleValue());
                        m.setMoneda(monedas.get(e.getKey()));
                        m.setDescripcion(descripcion);
                        m.setUsuario(usuario);
                        m.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
                        m.setActivo(true);
                        Long id = tesoreriaService.registrar(m).getId();
                        if (primero == null) primero = id;
                    }
                    return primero;
                },
                id -> id,
                this::loteYaRegistrado);
        return true;
    }

    /** Transfiere de una caja a otra en una o más monedas. Todo o nada. */
    @Transactional
    public Boolean transferir(Long origenId, Long destinoId, List<Monto> montos, String descripcion,
                              Usuario usuario, String claveIdempotencia) {
        exigirUsuario(usuario);
        if (origenId == null || destinoId == null) throw new GraphQLException("Debe indicar la caja origen y la destino.");
        if (origenId.equals(destinoId)) throw new GraphQLException("La caja origen y destino no pueden ser la misma");
        Map<Long, BigDecimal> porMoneda = validar(montos, false);

        HuellaPedido huella = new HuellaPedido().id(origenId).id(destinoId);
        porMoneda.forEach((monedaId, cantidad) -> huella.id(monedaId).numero(cantidad));
        huella.texto(descripcion);

        idempotenciaService.ejecutar(claveIdempotencia, OPERACION_TRANSFERENCIAS, huella.calcular(), usuario,
                () -> {
                    Map<Long, Moneda> monedas = monedas(porMoneda);
                    seguridad.requireEscrituraCaja(origenId);
                    seguridad.requireEscrituraCaja(destinoId);
                    List<Long> cajas = new ArrayList<>();
                    cajas.add(Math.min(origenId, destinoId));
                    cajas.add(Math.max(origenId, destinoId));
                    tomarSaldos(cajas, porMoneda);

                    Long primero = null;
                    for (Map.Entry<Long, BigDecimal> e : porMoneda.entrySet()) {
                        Long id = tesoreriaService.transferirYDevolverSalida(origenId, destinoId,
                                e.getValue().doubleValue(), monedas.get(e.getKey()), descripcion, usuario).getId();
                        if (primero == null) primero = id;
                    }
                    return primero;
                },
                id -> id,
                this::loteYaRegistrado);
        return true;
    }

    private static void exigirUsuario(Usuario usuario) {
        // El movimiento queda a nombre de quien está en la sesión, no de quien diga el pedido.
        if (usuario == null) throw new GraphQLException("No se pudo identificar al usuario de la sesión.");
    }

    /**
     * Valida los montos y los devuelve por moneda ascendente. Compara en {@link BigDecimal}: con
     * {@code Double}, un NaN pasa cualquier {@code <= 0} y revienta más adelante, a mitad del lote.
     */
    private static Map<Long, BigDecimal> validar(List<Monto> montos, boolean conSigno) {
        if (montos == null || montos.isEmpty()) throw new GraphQLException("Debe ingresar al menos un monto.");
        if (montos.size() > MAXIMO_DE_MONTOS) {
            throw new GraphQLException("Un pedido lleva como máximo " + MAXIMO_DE_MONTOS + " montos.");
        }
        Map<Long, BigDecimal> porMoneda = new TreeMap<>();
        for (Monto m : montos) {
            if (m == null || m.getMonedaId() == null) throw new GraphQLException("Cada monto debe indicar su moneda.");
            Double c = m.getCantidad();
            if (c == null || c.isNaN() || c.isInfinite()) throw new GraphQLException("Monto inválido.");
            BigDecimal cantidad = BigDecimal.valueOf(c).stripTrailingZeros();
            if (cantidad.signum() == 0) throw new GraphQLException("El monto no puede ser cero.");
            if (!conSigno && cantidad.signum() < 0) throw new GraphQLException("El monto debe ser mayor que cero.");
            if (cantidad.scale() > DECIMALES) {
                throw new GraphQLException("El monto admite hasta " + DECIMALES + " decimales.");
            }
            if (cantidad.abs().compareTo(TOPE) >= 0) throw new GraphQLException("Monto fuera de rango.");
            if (porMoneda.put(m.getMonedaId(), cantidad) != null) {
                throw new GraphQLException("La misma moneda aparece más de una vez en el pedido.");
            }
        }
        return porMoneda;
    }

    /** Una moneda que no existe se rechaza: {@code TesoreriaService.resolverMoneda(null)} la haría guaraníes. */
    private Map<Long, Moneda> monedas(Map<Long, BigDecimal> porMoneda) {
        Map<Long, Moneda> monedas = new LinkedHashMap<>();
        for (Long monedaId : porMoneda.keySet()) {
            monedas.put(monedaId, monedaRepository.findById(monedaId)
                    .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId)));
        }
        return monedas;
    }

    /** Lock de todos los saldos del lote, por (caja, moneda) ascendente. {@code cajas} llega ordenada. */
    private void tomarSaldos(List<Long> cajas, Map<Long, BigDecimal> porMoneda) {
        for (Long cajaId : cajas) {
            for (Long monedaId : porMoneda.keySet()) {
                saldoRepository.ensureRow(cajaId, monedaId);
                saldoRepository.lockByCajaVirtualIdAndMonedaId(cajaId, monedaId)
                        .orElseThrow(() -> new GraphQLException("No se pudo tomar el saldo de la caja"));
            }
        }
    }

    /**
     * El pedido ya se registró: devuelve su resultado, salvo que después se haya anulado (regla de la
     * idempotencia del módulo: ni éxito ni otro registro). Mira el primer movimiento del lote —y su otra
     * pata, si es una transferencia—: los movimientos de un lote no comparten ninguna columna.
     */
    private Long loteYaRegistrado(Long movimientoId) {
        Boolean activo = movimientoRepository.findActivoById(movimientoId).orElse(null);
        if (activo == null) return null;
        Long otraPata = movimientoRepository.findVinculoById(movimientoId)
                .filter(MovimientoCajaVirtualVinculo::esPataDeTransferencia)
                .map(MovimientoCajaVirtualVinculo::getReferenciaId).orElse(null);
        boolean otraActiva = otraPata == null || movimientoRepository.findActivoById(otraPata).orElse(true);
        if (!activo || !otraActiva) {
            throw new GraphQLException("Este pedido ya se registró y después fue anulado."
                    + " Si corresponde, cargalo de nuevo.");
        }
        return movimientoId;
    }
}
