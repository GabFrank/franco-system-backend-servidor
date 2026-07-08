package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.graphql.rrhh.input.LiquidacionConceptoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.LiquidacionConceptoService;
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
public class LiquidacionConceptoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LiquidacionConceptoService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<LiquidacionConcepto> liquidacionConcepto(Long id) {
        return service.findById(id);
    }

    public Optional<LiquidacionConcepto> liquidacionConceptoPorCodigo(String codigo) {
        return service.findByCodigo(codigo);
    }

    public List<LiquidacionConcepto> liquidacionConceptos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countLiquidacionConcepto() {
        return service.count();
    }

    public LiquidacionConcepto saveLiquidacionConcepto(LiquidacionConceptoInput input) {
        ModelMapper m = new ModelMapper();
        LiquidacionConcepto e;
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new LiquidacionConcepto());
            e.setUsuario(null);
            m.map(input, e);
        } else {
            e = m.map(input, LiquidacionConcepto.class);
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

    public Boolean deleteLiquidacionConcepto(Long id) {
        return service.deleteById(id);
    }
}
