package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.graphql.operaciones.input.PagoInput;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class PagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PagoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    public Pago pago(Long id){
        return service.findById(id).orElse(null);
    }

    public Pago savePago(PagoInput input) {
        ModelMapper m = new ModelMapper();
        Pago e = m.map(input, Pago.class);

        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getAutorizadoPorId() != null) {
            e.setAutorizadoPor(usuarioService.findById(input.getAutorizadoPorId()).orElse(null));
        }
        if(input.getCreadoEn() != null){
            e.setCreadoEn(stringToDate(input.getCreadoEn()));
        }

        return service.save(e);
    }
}

