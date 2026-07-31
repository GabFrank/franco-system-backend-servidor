package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoProveedor;
import com.franco.dev.domain.financiero.enums.FuentePago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.repository.financiero.MovimientoProveedorRepository;
import com.franco.dev.service.financiero.PagoProveedorService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class PagoProveedorGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final PagoProveedorService service;
    private final MovimientoProveedorRepository movimientoProveedorRepository;
    private final TesoreriaSecurityService seg;

    public Page<MovimientoProveedor> movimientosProveedor(Long proveedorId, int page, int size) {
        seg.requireVer();
        return movimientoProveedorRepository.findByProveedorIdOrderByCreadoEnDesc(proveedorId, PageRequest.of(page, size));
    }

    public SolicitudPago pagarSolicitud(Long solicitudId, List<LineaPagoInputWrapper> lineas) {
        seg.requirePagarCpp();
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
            ls.add(l);
        }
        return service.pagar(solicitudId, ls, seg.currentUsuario());
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
    }
}
