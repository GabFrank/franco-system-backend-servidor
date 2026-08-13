package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.enums.FuentePago;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.service.financiero.GastoTesoreriaService;
import com.franco.dev.service.financiero.PagoProveedorService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class PagoProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final PagoProveedorService service;
    private final GastoTesoreriaService gastoTesoreriaService;
    private final TesoreriaSecurityService seg;

    public List<SolicitudPago> solicitudesPagoPendientes(Long proveedorId) {
        seg.requireVer();
        return service.listarPendientes(proveedorId);
    }

    /** Gastos pagables (SolicitudPago tipo GASTO en SOLICITADO/PARCIAL) para el diálogo de gastos. */
    public List<SolicitudPago> gastosPendientes() {
        seg.requireVer();
        return service.listarGastosPendientes();
    }

    /** Crea un gasto (PreGasto liviano) + su obligación de pago (SolicitudPago GASTO, SOLICITADO). */
    public SolicitudPago crearGastoParaPago(GastoParaPagoWrapper input) {
        seg.requireGestionar();
        java.time.LocalDateTime venc = (input.getFechaVencimiento() != null && !input.getFechaVencimiento().isEmpty())
                ? com.franco.dev.utilitarios.DateUtils.stringToDate(input.getFechaVencimiento()) : null;
        return gastoTesoreriaService.crearGastoParaPago(
                input.getTipoGastoId(), input.getDescripcion(), input.getMonedaId(), input.getMonto(),
                input.getBeneficiarioProveedorId(), input.getBeneficiarioPersonaId(), venc,
                input.getSucursalId(), seg.currentUsuario());
    }

    public Pago pagarSolicitudesLoteCajaMayor(Long cajaVirtualId, List<PagoLoteWrapper> pagos) {
        seg.requirePagarCpp();
        List<PagoProveedorService.PagoLote> ls = new ArrayList<>();
        for (PagoLoteWrapper w : pagos) {
            PagoProveedorService.PagoLote p = new PagoProveedorService.PagoLote();
            p.setSolicitudId(w.getSolicitudId());
            p.setMonedaId(w.getMonedaId());
            p.setMonto(w.getMonto() != null ? BigDecimal.valueOf(w.getMonto()) : null);
            ls.add(p);
        }
        return service.pagarLoteCajaMayor(cajaVirtualId, ls, seg.currentUsuario());
    }

    public SolicitudPago pagarSolicitud(Long solicitudId, List<LineaPagoInputWrapper> lineas) {
        seg.requirePagarCpp();
        return service.pagar(solicitudId, mapLineas(lineas), seg.currentUsuario());
    }

    /** Pago mixto de varias solicitudes como un único evento consolidado. Devuelve el evento (Pago). */
    public Pago pagarSolicitudesMixto(List<SolicitudConLineasWrapper> pagos) {
        seg.requirePagarCpp();
        List<PagoProveedorService.SolicitudConLineas> ls = new ArrayList<>();
        for (SolicitudConLineasWrapper w : pagos) {
            PagoProveedorService.SolicitudConLineas s = new PagoProveedorService.SolicitudConLineas();
            s.setSolicitudId(w.getSolicitudId());
            s.setLineas(mapLineas(w.getLineas()));
            ls.add(s);
        }
        return service.pagarLoteMixto(ls, seg.currentUsuario());
    }

    /** Anula todo un evento de pago (revierte los movimientos consolidados y reabre sus solicitudes). */
    public Pago anularPagoCpp(Long pagoId, String motivo) {
        seg.requirePagarCpp();
        return service.anularPagoCpp(pagoId, motivo, seg.currentUsuario());
    }

    private List<PagoProveedorService.LineaPago> mapLineas(List<LineaPagoInputWrapper> lineas) {
        List<PagoProveedorService.LineaPago> ls = new ArrayList<>();
        for (LineaPagoInputWrapper w : lineas) {
            PagoProveedorService.LineaPago l = new PagoProveedorService.LineaPago();
            l.setFuente(w.getFuente());
            l.setCajaVirtualId(w.getCajaVirtualId());
            l.setCuentaBancariaId(w.getCuentaBancariaId());
            l.setMonedaId(w.getMonedaId());
            l.setMonto(w.getMonto() != null ? BigDecimal.valueOf(w.getMonto()) : null);
            l.setCotizacion(w.getCotizacion() != null ? BigDecimal.valueOf(w.getCotizacion()) : null);
            l.setMontoSolicitud(w.getMontoSolicitud() != null ? BigDecimal.valueOf(w.getMontoSolicitud()) : null);
            l.setDescuento(Boolean.TRUE.equals(w.getDescuento()));
            l.setAumento(Boolean.TRUE.equals(w.getAumento()));
            l.setChequeRef(w.getChequeRef());
            l.setChequeraId(w.getChequeraId());
            l.setDiferido(w.getDiferido());
            l.setNominal(w.getNominal());
            l.setBeneficiario(w.getBeneficiario());
            if (w.getFechaPago() != null && !w.getFechaPago().isEmpty()) {
                l.setFechaPago(com.franco.dev.utilitarios.DateUtils.stringToDate(w.getFechaPago()));
            }
            if (w.getFechaEmision() != null && !w.getFechaEmision().isEmpty()) {
                l.setFechaEmision(com.franco.dev.utilitarios.DateUtils.stringToDate(w.getFechaEmision()));
            }
            ls.add(l);
        }
        return ls;
    }

    @Data
    public static class GastoParaPagoWrapper {
        private Long tipoGastoId;
        private String descripcion;
        private Long monedaId;
        private Double monto;
        private Long beneficiarioProveedorId;
        private Long beneficiarioPersonaId;
        private String fechaVencimiento;
        private Long sucursalId;
    }

    @Data
    public static class PagoLoteWrapper {
        private Long solicitudId;
        private Long monedaId;
        private Double monto;
    }

    @Data
    public static class SolicitudConLineasWrapper {
        private Long solicitudId;
        private List<LineaPagoInputWrapper> lineas;
    }

    @Data
    public static class LineaPagoInputWrapper {
        private FuentePago fuente;
        private Long cajaVirtualId;
        private Long cuentaBancariaId;
        private Long monedaId;
        private Double monto;
        private Double cotizacion;
        private Double montoSolicitud;
        private Boolean descuento;   // línea de ajuste Fx: pagó de menos (ganancia)
        private Boolean aumento;     // línea de ajuste Fx: pagó de más (pérdida)
        // Fuente CHEQUE
        private Long chequeRef;
        private Long chequeraId;
        private Boolean diferido;
        private String fechaEmision;
        private String fechaPago;
        private String beneficiario;
        private Boolean nominal;
    }
}
