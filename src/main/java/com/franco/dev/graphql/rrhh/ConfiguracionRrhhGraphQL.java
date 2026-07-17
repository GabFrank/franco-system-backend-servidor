package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.ConfiguracionRrhh;
import com.franco.dev.graphql.rrhh.input.ConfiguracionRrhhInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.ConfiguracionRrhhService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ConfiguracionRrhhGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConfiguracionRrhhService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<ConfiguracionRrhh> configuracionRrhh(Long id) {
        return service.findById(id);
    }

    public Optional<ConfiguracionRrhh> configuracionRrhhPorClave(String clave) {
        return service.findByClave(clave);
    }

    public List<ConfiguracionRrhh> configuracionesRrhh(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<ConfiguracionRrhh> configuracionesRrhhSearch(String texto) {
        return service.findByAll(texto);
    }

    public Long countConfiguracionRrhh() {
        return service.count();
    }

    public ConfiguracionRrhh saveConfiguracionRrhh(ConfiguracionRrhhInput input) {
        ModelMapper m = new ModelMapper();
        // En update, m.map(input, e) sobre la entidad cargada: los campos null del
        // input (ej. creadoEn, que el form no envia) NO deben pisar los valores
        // existentes; si no, creado_en queda null y viola el NOT NULL al guardar.
        m.getConfiguration().setSkipNullEnabled(true);
        ConfiguracionRrhh e;
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new ConfiguracionRrhh());
            e.setUsuario(null);
            m.map(input, e);
        } else {
            e = m.map(input, ConfiguracionRrhh.class);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        } else if (input.getId() == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                usuarioService.findByNickname(authentication.getName()).ifPresent(e::setUsuario);
            }
        }
        return service.save(e);
    }

    public Boolean deleteConfiguracionRrhh(Long id) {
        return service.deleteById(id);
    }
}
