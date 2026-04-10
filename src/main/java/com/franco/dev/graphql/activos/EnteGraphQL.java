package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.graphql.activos.input.EnteInput;
import com.franco.dev.graphql.activos.input.EnteSucursalInput;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.activos.EnteSucursalService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.FuncionarioService;
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
public class EnteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EnteService service;

    @Autowired
    private EnteSucursalService enteSucursalService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<Ente> ente(Long id) {
        return service.findById(id).map(service::populateTransientFields);
    }

    public List<Ente> entes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<Ente> res = service.findAll(pageable);
        if (res != null) res.forEach(service::populateTransientFields);
        return res;
    }

    public Long countEnte() {
        return service.count();
    }

    public List<Ente> entesByTipoEnte(TipoEnte tipoEnte) {
        List<Ente> res = service.findByTipoEnte(tipoEnte);
        if (res != null) res.forEach(service::populateTransientFields);
        return res;
    }

    public Ente enteByReferenciaId(TipoEnte tipoEnte, Long referenciaId) {
        return service.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId).map(service::populateTransientFields).orElse(null);
    }

    public CustomPage<Ente> enteSearchPage(String texto, Long sucursalId, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<Ente> pageResult = service.findAllWithFilters(texto, sucursalId, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public Optional<EnteSucursal> enteSucursal(Long id) {
        Optional<EnteSucursal> res = enteSucursalService.findById(id);
        res.ifPresent(a -> {
            if (a.getEnte() != null) service.populateTransientFields(a.getEnte());
        });
        return res;
    }

    public List<EnteSucursal> entesSucursalesByEnteId(Long enteId) {
        List<EnteSucursal> res = enteSucursalService.findByEnteId(enteId);
        if (res != null) {
            res.forEach(a -> {
                if (a.getEnte() != null) service.populateTransientFields(a.getEnte());
            });
        }
        return res;
    }

    public List<EnteSucursal> entesSucursalesBySucursalId(Long sucursalId) {
        List<EnteSucursal> res = enteSucursalService.findBySucursalId(sucursalId);
        if (res != null) {
            res.forEach(a -> {
                if (a.getEnte() != null) service.populateTransientFields(a.getEnte());
            });
        }
        return res;
    }

    public CustomPage<EnteSucursal> enteSucursalSearchPage(String texto, Long sucursalId, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EnteSucursal> pageResult = enteSucursalService.findAllWithFilters(texto, sucursalId, p, s);
        pageResult.getContent().forEach(a -> {
            if (a.getEnte() != null) service.populateTransientFields(a.getEnte());
        });
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }


    public Ente saveEnte(EnteInput input) {
        Ente e = modelMapper.map(input, Ente.class);
        if (input.getTipoEnte() != null) {
            e.setTipoEnte(TipoEnte.valueOf(input.getTipoEnte()));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
            service.populateTransientFields(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el ente: " + err.getMessage());
        }
        return e;
    }

    public EnteSucursal saveEnteSucursal(EnteSucursalInput input) {
        EnteSucursal e;
        if (input.getId() != null) {
            e = enteSucursalService.findById(input.getId()).orElse(new EnteSucursal());
        } else {
            e = new EnteSucursal();
        }

        if (input.getEnteId() != null) {
            e.setEnte(service.findById(input.getEnteId()).orElse(null));
        }
        if (input.getSucursalId() != null) {
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getResponsableId() != null) {
            e.setResponsable(funcionarioService.findById(input.getResponsableId()).orElse(null));
        } else {
            e.setResponsable(null);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = enteSucursalService.save(e);
            if (e.getEnte() != null) service.populateTransientFields(e.getEnte());
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la asignación: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteEnte(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el ente: " + err.getMessage());
        }
    }

    public Boolean deleteEnteSucursal(Long id) {
        try {
            return enteSucursalService.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la asignación: " + err.getMessage());
        }
    }
}
