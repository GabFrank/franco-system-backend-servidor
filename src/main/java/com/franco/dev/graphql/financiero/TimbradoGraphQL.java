package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.TimbradoInput;
import com.franco.dev.service.financiero.TimbradoService;
import com.franco.dev.service.financiero.TimbradoDetalleService;
import com.franco.dev.service.personas.UsuarioService;
import org.springframework.data.domain.Page;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import com.franco.dev.utilitarios.DateUtils;

@Component
public class TimbradoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TimbradoService service;

    @Autowired
    private TimbradoDetalleService timbradoDetalleService;

    @Autowired
    private UsuarioService usuarioService;


    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Timbrado> timbrado(Long id) {
        return service.findById(id);
    }

    public List<Timbrado> timbrados(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }
    
    public List<Timbrado> timbradosSearch(String texto) {
        return service.findByAll(texto);
    }

    public Page<Timbrado> findTimbradoByNumero(String numero, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByNumeroLike(numero, pageable);
    }

    public Timbrado saveTimbrado(TimbradoInput input) {
        ModelMapper m = new ModelMapper();
        Timbrado e = m.map(input, Timbrado.class);
        
        // Usar DateUtils para conversión String a LocalDateTime
        if (input.getFechaInicio() != null && !input.getFechaInicio().isEmpty()) {
            e.setFechaInicio(DateUtils.stringToDate(input.getFechaInicio()));
        }
        if (input.getFechaFin() != null && !input.getFechaFin().isEmpty()) {
            e.setFechaFin(DateUtils.stringToDate(input.getFechaFin()));
        }
        // Preservar fecha de creación al editar (no permitir que se sobrescriba desde el frontend)
        if (input.getCreadoEn() != null && !input.getCreadoEn().isEmpty()) {
            e.setCreadoEn(DateUtils.stringToDate(input.getCreadoEn()));
        }
        
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        e = service.save(e);
        return e;
    }

    public Boolean deleteTimbrado(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countTimbrado() {
        return service.count();
    }

    public Boolean existeTimbradoActivo(Long excludeId) {
        return service.existeTimbradoActivo(excludeId);
    }

    public Page<TimbradoDetalle> findTimbradoDetallesByTimbradoId(Long timbradoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 10);
        return timbradoDetalleService.findByTimbradoId(timbradoId, pageable);
    }


}
