package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.OperacionFinancieraService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.Data;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OperacionFinancieraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final OperacionFinancieraService service;
    private final MovimientoBancarioRepository movimientoBancarioRepository;
    private final CajaVirtualService cajaVirtualService;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final MonedaService monedaService;
    private final com.franco.dev.repository.financiero.OperacionFinancieraCategoriaRepository categoriaRepository;
    private final TesoreriaSecurityService seg;

    public OperacionFinancieraGraphQL(OperacionFinancieraService service,
                                      MovimientoBancarioRepository movimientoBancarioRepository,
                                      CajaVirtualService cajaVirtualService,
                                      CuentaBancariaRepository cuentaBancariaRepository,
                                      MonedaService monedaService,
                                      com.franco.dev.repository.financiero.OperacionFinancieraCategoriaRepository categoriaRepository,
                                      TesoreriaSecurityService seg) {
        this.service = service;
        this.movimientoBancarioRepository = movimientoBancarioRepository;
        this.cajaVirtualService = cajaVirtualService;
        this.cuentaBancariaRepository = cuentaBancariaRepository;
        this.monedaService = monedaService;
        this.categoriaRepository = categoriaRepository;
        this.seg = seg;
    }

    public java.util.List<com.franco.dev.domain.financiero.OperacionFinancieraCategoria> operacionFinancieraCategorias() {
        seg.requireVer();
        return categoriaRepository.findByActivoTrue();
    }

    public Page<OperacionFinanciera> operacionesFinancieras(int page, int size) {
        seg.requireVer();
        return service.findAll(PageRequest.of(page, size));
    }

    public OperacionFinanciera operacionFinanciera(Long id) {
        seg.requireVer();
        return service.porId(id);
    }

    public Page<MovimientoBancario> movimientosBancarios(Long cuentaBancariaId, int page, int size) {
        seg.requireVer();
        return movimientoBancarioRepository.findByCuentaBancariaIdOrderByCreadoEnDesc(cuentaBancariaId, PageRequest.of(page, size));
    }

    public OperacionFinanciera registrarOperacionFinanciera(OperacionFinancieraInputWrapper in) {
        seg.requireGestionar();
        OperacionFinanciera op = new OperacionFinanciera();
        op.setTipoOperacion(in.getTipoOperacion());
        op.setDescripcion(in.getDescripcion());
        op.setCotizacion(in.getCotizacion() != null ? BigDecimal.valueOf(in.getCotizacion()) : null);
        op.setNumeroComprobante(in.getNumeroComprobante());
        op.setMontoOrigen(in.getMontoOrigen() != null ? BigDecimal.valueOf(in.getMontoOrigen()) : null);
        op.setMontoDestino(in.getMontoDestino() != null ? BigDecimal.valueOf(in.getMontoDestino()) : null);
        op.setDiferencia(in.getDiferencia() != null ? BigDecimal.valueOf(in.getDiferencia()) : null);
        op.setDiferenciaDestinoTipo(in.getDiferenciaDestinoTipo());
        op.setDiferenciaObservacion(in.getDiferenciaObservacion());
        if (in.getCajaMayorOrigenId() != null) op.setCajaMayorOrigen(cajaVirtualService.findById(in.getCajaMayorOrigenId()).orElse(null));
        if (in.getCajaMayorDestinoId() != null) op.setCajaMayorDestino(cajaVirtualService.findById(in.getCajaMayorDestinoId()).orElse(null));
        if (in.getCuentaBancariaOrigenId() != null) op.setCuentaBancariaOrigen(cuentaBancariaRepository.findById(in.getCuentaBancariaOrigenId()).orElse(null));
        if (in.getCuentaBancariaDestinoId() != null) op.setCuentaBancariaDestino(cuentaBancariaRepository.findById(in.getCuentaBancariaDestinoId()).orElse(null));
        if (in.getMonedaOrigenId() != null) op.setMonedaOrigen(monedaService.findById(in.getMonedaOrigenId()).orElse(null));
        if (in.getMonedaDestinoId() != null) op.setMonedaDestino(monedaService.findById(in.getMonedaDestinoId()).orElse(null));
        if (in.getCategoriaId() != null) op.setCategoria(categoriaRepository.findById(in.getCategoriaId()).orElse(null));
        return service.registrar(op, seg.currentUsuario());
    }

    public OperacionFinanciera anularOperacionFinanciera(Long id, String motivo) {
        seg.requireGestionar();
        return service.anular(id, motivo, seg.currentUsuario());
    }

    @Data
    public static class OperacionFinancieraInputWrapper {
        private TipoOperacionFinanciera tipoOperacion;
        private String descripcion;
        private Long categoriaId;
        private Long cajaMayorOrigenId;
        private Long cuentaBancariaOrigenId;
        private Long monedaOrigenId;
        private Double montoOrigen;
        private Long cajaMayorDestinoId;
        private Long cuentaBancariaDestinoId;
        private Long monedaDestinoId;
        private Double montoDestino;
        private Double cotizacion;
        private String numeroComprobante;
        private Double diferencia;
        private com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo diferenciaDestinoTipo;
        private String diferenciaObservacion;
    }
}
