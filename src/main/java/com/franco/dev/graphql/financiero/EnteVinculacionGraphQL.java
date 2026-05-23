package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.financiero.EnteVinculacion;
import com.franco.dev.graphql.financiero.input.EnteVinculacionInput;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.EnteVinculacionService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.utilitarios.DateUtils;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class EnteVinculacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EnteVinculacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EnteService enteService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<EnteVinculacion> enteVinculacion(Long id) {
        return service.findById(id);
    }

    public List<EnteVinculacion> enteVinculaciones(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countEnteVinculacion() {
        return service.count();
    }

    public List<EnteVinculacion> enteVinculacionesByEnte(Long enteId) {
        return service.findByEnteId(enteId);
    }

    public List<EnteVinculacion> enteVinculacionesBySucursal(Long sucursalId) {
        return service.findBySucursalId(sucursalId);
    }

    public CustomPage<EnteVinculacion> enteVinculacionSearchPage(Long enteId, Long sucursalId, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EnteVinculacion> pageResult = service.findAllByEnteIdAndSucursalId(enteId, sucursalId, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public EnteVinculacion saveEnteVinculacion(EnteVinculacionInput input) {
        EnteVinculacion e = modelMapper.map(input, EnteVinculacion.class);
        if (input.getEnteId() != null) {
            e.setEnte(enteService.findById(input.getEnteId()).orElse(null));
        }
        if (input.getSucursalId() != null) {
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getAlquilerProveedorId() != null) {
            e.setAlquilerProveedor(personaService.findById(input.getAlquilerProveedorId()).orElse(null));
        }
        if (input.getAlquilerVigencia() != null && !input.getAlquilerVigencia().isEmpty()) {
            LocalDate fecha = DateUtils.stringToDate(input.getAlquilerVigencia()).toLocalDate();
            e.setAlquilerVigencia(fecha);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la vinculación del ente: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteEnteVinculacion(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la vinculación del ente: " + err.getMessage());
        }
    }
}
