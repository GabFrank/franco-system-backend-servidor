package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Feriado;
import com.franco.dev.graphql.rrhh.input.FeriadoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.FeriadoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToLocalDate;

@Component
public class FeriadoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FeriadoService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Feriado> feriado(Long id) {
        return service.findById(id);
    }

    public List<Feriado> feriados(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Feriado> feriadosPorRango(String desde, String hasta) {
        return service.findByFechaBetween(
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    public List<Feriado> feriadosActivos() {
        return service.findActivos();
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<Feriado> feriadosPage(int page, int size, String desde, String hasta, String descripcion,
                                      Boolean activo) {
        return service.findPage(stringToLocalDate(desde), stringToLocalDate(hasta), descripcion, activo,
                PageRequest.of(page, size));
    }

    public Long countFeriado() {
        return service.count();
    }

    public Feriado saveFeriado(FeriadoInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        Feriado e = input.getId() != null
                ? service.findById(input.getId()).orElse(new Feriado())
                : new Feriado();
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        e.setDescripcion(input.getDescripcion());
        e.setEsNacional(input.getEsNacional());
        e.setRecargoPorcentaje(input.getRecargoPorcentaje());
        e.setActivo(input.getActivo());
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

    public Boolean deleteFeriado(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.deleteById(id);
    }
}
