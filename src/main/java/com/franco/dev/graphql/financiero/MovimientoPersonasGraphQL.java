package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.graphql.financiero.input.MovimientoPersonasInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.MovimientoPersonasService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class MovimientoPersonasGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MovimientoPersonasService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<MovimientoPersonas> movimientoPersona(Long id) {
        return service.findById(id);
    }

    public List<MovimientoPersonas> movimientoPersonas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public MovimientoPersonas saveMovimientoPersonas(MovimientoPersonasInput input) {
        ModelMapper m = new ModelMapper();
        MovimientoPersonas e = m.map(input, MovimientoPersonas.class);
        if(input.getVencimiento()!=null) e.setVencimiento(toDate(input.getVencimiento()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getPersonaId() != null) e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.BANCO);
        return e;
    }

    public List<MovimientoPersonas> movimientoPersonasPorPersonaAndVencimiento(Long id, String inicio, String fin) {
        return service.findByPersonaAndVencimiento(id, toDate(inicio), toDate(fin), null);
    }

    public Boolean deleteMovimientoPersonas(Long id) {
        Boolean ok = service.deleteById(id);
        if (ok) propagacionService.eliminarEntidad(id, TipoEntidad.BANCO);
        return ok;
    }

    public Long countMovimientoPersonas() {
        return service.count();
    }


}
