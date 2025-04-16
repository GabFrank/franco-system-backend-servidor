package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalle;
import com.franco.dev.domain.operaciones.enums.PagoDetalleEstado;
import com.franco.dev.graphql.operaciones.input.PagoDetalleInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.PagoDetalleService;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.util.List;

@Component
public class PagoDetalleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PagoDetalleService service;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private FormaPagoService formaPagoService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PdvCajaService pdvCajaService;

    public PagoDetalle pagoDetalle(Long id){
        return service.findById(id).orElse(null);
    }
    
    public List<PagoDetalle> pagoDetallesPorPagoId(Long pagoId) {
        return service.findByPagoId(pagoId);
    }

    public PagoDetalle savePagoDetalle(PagoDetalleInput input) {
        ModelMapper m = new ModelMapper();
        PagoDetalle e = m.map(input, PagoDetalle.class);

        if (input.getPagoId() != null) {
            e.setPago(pagoService.findById(input.getPagoId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getFormaPagoId() != null) {
            e.setFormaPago(formaPagoService.findById(input.getFormaPagoId()).orElse(null));
        }
        if (input.getCajaId() != null) {
            e.setCaja(pdvCajaService.getRepository().findByIdAndSucursalId(input.getCajaId(), input.getSucursalId()));
        }
        if(input.getFechaProgramado() != null){
            e.setFechaProgramado(stringToDate(input.getFechaProgramado()));
        }
        if(input.getCreadoEn() != null){
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        }
        if(input.getEstado() == null) {
            e.setEstado(PagoDetalleEstado.ABIERTO);
        }

        return service.save(e);
    }
    
    public Boolean deletePagoDetalle(Long id) {
        return service.deleteById(id);
    }

    public PagoDetalle updatePagoDetalleCajaySucursal(Long pagoDetalleId, Long sucursalId, Long cajaId) {
        if(pagoDetalleId == null || sucursalId == null) {
            return null;
        }
        PagoDetalle pagoDetalle = service.findById(pagoDetalleId).orElse(null);
        if(pagoDetalle == null) {
            return null;
        }
        
        if(cajaId != null) {
            pagoDetalle.setCaja(pdvCajaService.getRepository().findByIdAndSucursalId(cajaId, sucursalId));
        } else {
            pagoDetalle.setCaja(null);
        }
        
        return service.save(pagoDetalle);
    }
}

