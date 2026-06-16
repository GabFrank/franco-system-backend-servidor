package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.EnteArchivo;
import com.franco.dev.graphql.activos.input.EnteArchivoInput;
import com.franco.dev.service.activos.EnteArchivoDocumentService;
import com.franco.dev.service.activos.EnteArchivoService;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collections;
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
    private EnteArchivoDocumentService documentService;

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

    public List<String> getEnteArchivoContenido(Long id) {
        EnteArchivo archivo = service.findById(id).orElse(null);
        if (archivo == null || archivo.getUrl() == null || archivo.getUrl().isBlank()) {
            return Collections.emptyList();
        }
        String url = archivo.getUrl().trim();
        if (esUrlExterna(url)) {
            return Collections.emptyList();
        }
        return documentService.getContenidoBase64(url);
    }

    public CustomPage<EnteArchivo> enteArchivoSearchPage(Long enteId, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EnteArchivo> pageResult = service.findAllByEnteId(enteId, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public EnteArchivo saveEnteArchivo(EnteArchivoInput input) {
        EnteArchivo e = input.getId() != null
                ? service.findById(input.getId()).orElse(new EnteArchivo())
                : new EnteArchivo();

        e.setDescripcion(input.getDescripcion());
        e.setVigente(input.getVigente());

        if (input.getEnteId() != null) {
            e.setEnte(enteService.findById(input.getEnteId()).orElse(null));
        }
        if (input.getTipoArchivo() != null) {
            e.setTipoArchivo(input.getTipoArchivo());
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }

        if (input.getContenidoBase64() != null && !input.getContenidoBase64().isBlank()) {
            try {
                String tipo = input.getTipoArchivo() != null ? input.getTipoArchivo().name() : "OTRO";
                String fileName = documentService.guardar(
                        input.getEnteId(),
                        tipo,
                        input.getNombreArchivo(),
                        input.getContenidoBase64());
                e.setUrl(fileName);
                if (e.getDescripcion() == null || e.getDescripcion().isBlank()) {
                    e.setDescripcion(input.getNombreArchivo());
                }
            } catch (Exception ex) {
                throw new GraphQLException("No se pudo guardar el archivo en el servidor: " + ex.getMessage());
            }
        } else if (input.getUrl() != null && !input.getUrl().isBlank()) {
            if (esUrlExterna(input.getUrl())) {
                throw new GraphQLException("No se permiten URLs externas. Suba el archivo al servidor local.");
            }
            e.setUrl(input.getUrl());
        }

        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el archivo del ente: " + err.getMessage());
        }
        return e;
    }

    private boolean esUrlExterna(String url) {
        String value = url.trim().toLowerCase();
        return value.startsWith("http://") || value.startsWith("https://");
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
