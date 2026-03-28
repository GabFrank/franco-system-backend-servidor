package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.graphql.activos.input.InmuebleInput;
import com.franco.dev.service.activos.InmuebleService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class InmuebleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InmuebleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CiudadService ciudadService;

    @Autowired
    private MonedaService monedaService;

    public Optional<Inmueble> inmueble(Long id) {
        return service.findById(id);
    }

    public List<Inmueble> inmuebles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countInmueble() {
        return service.count();
    }

    public List<Inmueble> inmuebleSearch(String texto) {
        return service.findByAll(texto);
    }

    public CustomPage<Inmueble> inmuebleSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<Inmueble> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public Inmueble saveInmueble(InmuebleInput input) {
        Inmueble e = new Inmueble();
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new Inmueble());
        }
        e.setNombreAsignado(input.getNombreAsignado());
        e.setDireccion(input.getDireccion());
        e.setGoogleMapsUrl(input.getGoogleMapsUrl());
        e.setCodigoCatastral(input.getCodigoCatastral());
        e.setValorTasacion(input.getValorTasacion());

        if (input.getPropietarioId() != null) {
            e.setPropietario(personaService.findById(input.getPropietarioId()).orElse(null));
        }
        if (input.getPaisId() != null) {
            e.setPais(paisService.findById(input.getPaisId()).orElse(null));
        }
        if (input.getCiudadId() != null) {
            e.setCiudad(ciudadService.findById(input.getCiudadId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        
        e.setSituacionPago(input.getSituacionPago());
        e.setMontoTotal(input.getMontoTotal());
        e.setMontoYaPagado(input.getMontoYaPagado());
        e.setCantidadCuotas(input.getCantidadCuotas());
        e.setDiaVencimiento(input.getDiaVencimiento());

        if (input.getProveedorId() != null) {
            e.setProveedor(personaService.findById(input.getProveedorId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }

        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el inmueble: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteInmueble(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el inmueble: " + err.getMessage());
        }
    }
}
