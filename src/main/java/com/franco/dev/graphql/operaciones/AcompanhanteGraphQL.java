package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Acompanhante;
import com.franco.dev.graphql.operaciones.input.AcompanhanteInput;
import com.franco.dev.service.operaciones.AcompanhanteService;
import com.franco.dev.service.operaciones.HojaRutaService;
import com.franco.dev.service.personas.PersonaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AcompanhanteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private AcompanhanteService service;

    @Autowired
    private HojaRutaService hojaRutaService;

    @Autowired
    private PersonaService personaService;

    public List<Acompanhante> acompanhantesPorHojaRuta(Long hojaRutaId) {
        return service.findByHojaRutaId(hojaRutaId);
    }

    public Acompanhante saveAcompanhante(AcompanhanteInput input) {
        Acompanhante a = new Acompanhante();
        Acompanhante.AcompanhanteId id = new Acompanhante.AcompanhanteId(input.getHojaRutaId(), input.getPersonaId());
        a.setId(id);
        a.setHojaRuta(hojaRutaService.findById(input.getHojaRutaId()).orElse(null));
        a.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        return service.save(a);
    }

    public Boolean deleteAcompanhante(Long hojaRutaId, Long personaId) {
        Acompanhante.AcompanhanteId id = new Acompanhante.AcompanhanteId(hojaRutaId, personaId);
        return service.deleteById(id);
    }
}
