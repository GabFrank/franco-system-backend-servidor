package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.EnteArchivo;
import com.franco.dev.graphql.activos.input.EnteArchivoInput;
import com.franco.dev.service.activos.EnteArchivoService;
import com.franco.dev.service.activos.EnteService;
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
public class EnteArchivoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EnteArchivoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EnteService enteService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<EnteArchivo> enteArchivo(Long id) {
        return service.findById(id);
    }

    public List<EnteArchivo> enteArchivos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countEnteArchivo() {
        return service.count();
    }

    public List<EnteArchivo> enteArchivosByEnte(Long enteId) {
        return service.findByEnteId(enteId);
    }

    public List<EnteArchivo> enteArchivosVigentesByEnte(Long enteId) {
        return service.findVigentesByEnteId(enteId);
    }

    public CustomPage<EnteArchivo> enteArchivoSearchPage(Long enteId, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EnteArchivo> pageResult = service.findAllByEnteId(enteId, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public EnteArchivo saveEnteArchivo(EnteArchivoInput input) {
        EnteArchivo e = modelMapper.map(input, EnteArchivo.class);
        if (input.getEnteId() != null) {
            e.setEnte(enteService.findById(input.getEnteId()).orElse(null));
        }
        if (input.getTipoArchivo() != null) {
            e.setTipoArchivo(input.getTipoArchivo());
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el archivo del ente: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteEnteArchivo(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el archivo del ente: " + err.getMessage());
        }
    }
}
