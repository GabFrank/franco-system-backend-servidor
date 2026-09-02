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
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<LiquidacionConcepto> liquidacionConcepto(Long id) {
        return service.findById(id);
    }

    /** Conceptos elegibles al agregar un item manual a una liquidacion. */
    public List<LiquidacionConcepto> liquidacionConceptosParaItemManual() {
        seg.requireVer();
        return service.findParaItemManual();
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
        seg.requireAnyRole(seg.CONFIG, seg.GESTIONAR);
        ModelMapper m = new ModelMapper();
        // Ver ConfiguracionRrhhGraphQL: en update, los campos null del input no deben
        // pisar valores existentes (ej. creadoEn) y disparar NOT NULL al guardar.
        m.getConfiguration().setSkipNullEnabled(true);
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
        seg.requireAnyRole(seg.CONFIG, seg.GESTIONAR);
        return service.deleteById(id);
    }
}
