package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoInput;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.util.List;

@Component
public class SolicitudPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SolicitudPagoService service;

    @Autowired
    private UsuarioService usuarioService;

    public List<SolicitudPago> solicitudPagoPorUsuarioId(Long id){
        return service.getRepository().findByUsuarioId(id);
    }

    public SolicitudPago solicitudPago(Long id){
        return service.findById(id).orElse(null);
    }
    
    /**
     * Query to filter SolicitudPago by multiple criteria
     * @param solicitudPagoId The ID of the solicitud pago to filter by (optional)
     * @param referenciaId The reference ID to filter by (optional)
     * @param tipo The tipo to filter by (optional)
     * @param estado The estado to filter by (optional)
     * @param fechaInicio The start date to filter by (optional)
     * @param fechaFin The end date to filter by (optional)
     * @param page The page number (default: 0)
     * @param size The page size (default: 20)
     * @return A page of SolicitudPago that match the filter criteria
     */
    public Page<SolicitudPago> solicitudPagoConFiltros(
            Long solicitudPagoId,
            Long referenciaId,
            TipoSolicitudPago tipo,
            SolicitudPagoEstado estado,
            String fechaInicio,
            String fechaFin,
            Integer page,
            Integer size) {
        
        return service.findAllWithFilters(
                solicitudPagoId,
                referenciaId,
                tipo,
                estado,
                fechaInicio,
                fechaFin,
                page,
                size
        );
    }

    public SolicitudPago saveSolicitudPago(SolicitudPagoInput input) {
        ModelMapper m = new ModelMapper();
        SolicitudPago e = m.map(input, SolicitudPago.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getCreadoEn() != null){
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        }
        SolicitudPago solicitudPago = service.save(e);
        return solicitudPago;
    }
}

