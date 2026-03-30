package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaObservacion;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaObservacion;
import com.franco.dev.graphql.financiero.input.CajaCategoriaObservacionInput;
import com.franco.dev.graphql.financiero.input.CajaObservacionInput;
import com.franco.dev.graphql.operaciones.input.VentaObservacionInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CajaMotivoObservacionService;
import com.franco.dev.service.financiero.CajaObservacionService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CajaObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {
    @Autowired
    private CajaObservacionService service;

    @Autowired
    private CajaMotivoObservacionService cajaMotivoObservacionService;

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<CajaObservacion> cajaObservacion(Long cajaId) {
        return service.findById(cajaId);
    }

    public List<CajaObservacion> cajaObservaciones() {
        return service.findAll2();
    }

    public List<CajaObservacion> findByPdvCajaIdAndSucursalId(Long cajaId, Long sucursalId) {
        return service.findByPdvCajaIdAndSucursalId(cajaId, sucursalId);
    }

    public CajaObservacion saveCajaObservacion(CajaObservacionInput input) {
        ModelMapper m = new  ModelMapper();
        CajaObservacion e = m.map(input, CajaObservacion.class);
        CajaObservacion cajaObservacion = null;
        if (input.getCajaMotivoObservacionId() != null) {
            e.setCajaMotivoObservacion(cajaMotivoObservacionService.findById(input.getCajaMotivoObservacionId()).orElse(null));
        }
       if (input.getCajaId() != null && input.getSucursalId() != null) {

            PdvCaja pdvCaja = pdvCajaService.findById(input.getCajaId(), input.getSucursalId());
            if (pdvCaja == null) {
                throw new IllegalArgumentException(
                        "Venta not found for ventaId: " + input.getCajaId()
                                + " and sucursalId: " + input.getSucursalId());
            }
            e.setPdvCaja(pdvCaja);
            }
        if (input.getSucursalId() != null)
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        if(input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getId() != null)
            cajaObservacion = service.findById(input.getId()).orElse(null);
        return service.save(e);
    }

    public Boolean deleteCajaObservacion(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
