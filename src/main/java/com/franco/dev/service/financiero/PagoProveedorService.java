package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.MovimientoProveedor;
import com.franco.dev.domain.financiero.enums.*;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * CPP — pago de solicitudes de pago (proveedor). SolicitudPago actúa como CPP
 * (central-only). El pago soporta <b>varias líneas</b> (pago mixto) con distinta
 * fuente (caja mayor / cuenta bancaria; cheque llega en F7), cada una posteando su
 * egreso y sumando al monto pagado. Registra un PAGO en el libro del proveedor
 * (baja {@code Proveedor.saldoActual}) y transiciona la solicitud
 * PENDIENTE→PARCIAL→CONCLUIDO. Atómico.
 */
@Service
@AllArgsConstructor
public class PagoProveedorService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final SolicitudPagoService solicitudPagoService;
    private final TesoreriaService tesoreriaService;
    private final BancoLedgerService bancoLedgerService;
    private final ProveedorCuentaService proveedorCuentaService;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final MonedaRepository monedaRepository;

    /** Una línea de pago (pago mixto). */
    @Data
    public static class LineaPago {
        private FuentePago fuente;
        private Long cajaVirtualId;
        private Long cuentaBancariaId;
        private Long monedaId;
        private BigDecimal monto;          // monto de la línea en su moneda
        private BigDecimal cotizacion;     // hacia la moneda de la solicitud
        private BigDecimal montoSolicitud; // monto convertido a la moneda de la solicitud
    }

    @Transactional
    public SolicitudPago pagar(Long solicitudId, List<LineaPago> lineas, Usuario usuario) {
        SolicitudPago sp = solicitudPagoService.findById(solicitudId).orElse(null);
        if (sp == null) throw new GraphQLException("Solicitud de pago no encontrada: " + solicitudId);
        if (sp.getEstado() == SolicitudPagoEstado.CONCLUIDO || sp.getEstado() == SolicitudPagoEstado.CANCELADO) {
            throw new GraphQLException("La solicitud ya está " + sp.getEstado());
        }
        if (lineas == null || lineas.isEmpty()) throw new GraphQLException("Debe indicar al menos una línea de pago");

        BigDecimal total = BigDecimal.valueOf(sp.getMontoTotal() != null ? sp.getMontoTotal() : 0.0);
        BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal restante = total.subtract(pagado);

        BigDecimal aplicadoSolicitud = BigDecimal.ZERO;
        for (LineaPago l : lineas) {
            BigDecimal montoSol = l.getMontoSolicitud() != null ? l.getMontoSolicitud() : l.getMonto();
            if (montoSol == null || montoSol.signum() <= 0) throw new GraphQLException("Monto de línea inválido");
            postearEgreso(sp, l, usuario);
            aplicadoSolicitud = aplicadoSolicitud.add(montoSol);
        }
        if (aplicadoSolicitud.subtract(restante).compareTo(TOLERANCIA) > 0) {
            throw new GraphQLException("El pago excede el saldo de la solicitud (" + restante + ")");
        }

        // Libro del proveedor (PAGO)
        if (sp.getProveedor() != null) {
            MovimientoProveedor plantilla = new MovimientoProveedor();
            plantilla.setSolicitudPagoId(sp.getId());
            plantilla.setDescripcion("Pago solicitud #" + sp.getId());
            proveedorCuentaService.registrar(sp.getProveedor().getId(), MovimientoProveedorTipo.PAGO, aplicadoSolicitud, plantilla, usuario);
        }

        // Actualizar solicitud + estado
        BigDecimal nuevoPagado = pagado.add(aplicadoSolicitud);
        sp.setMontoPagado(nuevoPagado);
        boolean concluido = nuevoPagado.subtract(total).abs().compareTo(TOLERANCIA) <= 0
                || nuevoPagado.compareTo(total) >= 0;
        sp.setEstado(concluido ? SolicitudPagoEstado.CONCLUIDO : SolicitudPagoEstado.PARCIAL);
        return solicitudPagoService.save(sp);
    }

    private void postearEgreso(SolicitudPago sp, LineaPago l, Usuario usuario) {
        BigDecimal monto = l.getMonto();
        if (monto == null || monto.signum() <= 0) throw new GraphQLException("Monto de línea inválido");
        Moneda moneda = l.getMonedaId() != null ? monedaRepository.findById(l.getMonedaId()).orElse(null) : null;

        if (l.getFuente() == FuentePago.CAJA_MAYOR) {
            CajaVirtual caja = cajaVirtualRepository.findById(l.getCajaVirtualId())
                    .orElseThrow(() -> new GraphQLException("Caja mayor no encontrada"));
            MovimientoCajaVirtual m = new MovimientoCajaVirtual();
            m.setCajaVirtual(caja);
            m.setTipoMovimiento(CajaVirtualTipoMovimiento.PAGO_PROVEEDOR);
            m.setCantidad(monto.doubleValue());
            m.setMoneda(moneda);
            m.setUsuario(usuario);
            m.setDescripcion("Pago proveedor (solicitud #" + sp.getId() + ")");
            m.setReferenciaId(sp.getId());
            m.setOrigenTipo(OrigenMovimientoTipo.PAGO_CPP);
            m.setOrigenId(sp.getId());
            tesoreriaService.registrar(m);
        } else if (l.getFuente() == FuentePago.CUENTA_BANCARIA) {
            bancoLedgerService.registrar(l.getCuentaBancariaId(), MovimientoBancarioTipo.SALIDA_MANUAL, monto,
                    "Pago proveedor (solicitud #" + sp.getId() + ")",
                    OrigenMovimientoTipo.PAGO_CPP.name(), sp.getId(), usuario);
        } else {
            // FuentePago.CHEQUE se implementa en F7 (emisión de cheque desde el pago).
            throw new GraphQLException("Pago con cheque disponible en la fase de cheques (F7)");
        }
    }
}
