package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.MotivoVale;
import com.franco.dev.graphql.rrhh.input.MotivoValeInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.MotivoValeService;
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

@Component
public class MotivoValeGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MotivoValeService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<MotivoVale> motivoVale(Long id) {
        return service.findById(id);
    }

    public List<MotivoVale> motivosVale(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<MotivoVale> motivosValeActivos() {
        return service.findActivos();
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<MotivoVale> motivosValePage(int page, int size, String descripcion, Boolean activo) {
        return service.findPage(descripcion, activo, PageRequest.of(page, size));
    }

    public MotivoVale saveMotivoVale(MotivoValeInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        MotivoVale e = input.getId() != null
                ? service.findById(input.getId()).orElse(new MotivoVale())
                : new MotivoVale();
        e.setNombre(input.getNombre());
        e.setDescripcion(input.getDescripcion());
        e.setActivo(input.getActivo());
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        } else if (input.getId() == null) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.isAuthenticated()) {
                usuarioService.findByNickname(a.getName()).ifPresent(e::setUsuario);
            }
        }
        return service.save(e);
    }

    public Boolean deleteMotivoVale(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.deleteById(id);
    }
}
