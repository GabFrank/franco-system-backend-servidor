package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.graphql.financiero.input.EnteFinancieroInput;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.financiero.EnteFinancieroService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EnteFinancieroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EnteFinancieroService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EnteService enteService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<EnteFinanciero> enteFinanciero(Long id) {
        return service.findById(id);
    }

    public List<EnteFinanciero> entesFinanciero(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countEnteFinanciero() {
        return service.count();
    }

    public EnteFinanciero enteFinancieroByEnte(Long enteId) {
        return service.findByEnteId(enteId).orElse(null);
    }

    public CustomPage<EnteFinanciero> enteFinancieroSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EnteFinanciero> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public EnteFinanciero saveEnteFinanciero(EnteFinancieroInput input) {
        EnteFinanciero e = modelMapper.map(input, EnteFinanciero.class);
        if (input.getEnteId() != null) {
            e.setEnte(enteService.findById(input.getEnteId()).orElse(null));
        }
        if (input.getSituacionPago() != null) {
            e.setSituacionPago(input.getSituacionPago());
        }
        if (input.getProveedorId() != null) {
            e.setProveedor(personaService.findById(input.getProveedorId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el ente financiero: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteEnteFinanciero(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el ente financiero: " + err.getMessage());
        }
    }
}
