package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.graphql.operaciones.input.PagoInput;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PagoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    @Autowired
    private TesoreriaSecurityService seg;

    public Pago pago(Long id){
        seg.requireVer();
        return service.findById(id).orElse(null);
    }

    /** Pantalla vieja de pagos. Estados y campos que se pueden tocar: ver {@link PagoService#guardarManual}. */
    public Pago savePago(PagoInput input) {
        seg.requireGestionar();
        return service.guardarManual(input.getId(), input.getEstado(), input.getProgramado(),
                input.getUsuarioId() != null ? usuarioService.findById(input.getUsuarioId()).orElse(null) : null,
                input.getAutorizadoPorId() != null ? usuarioService.findById(input.getAutorizadoPorId()).orElse(null) : null);
    }
}

