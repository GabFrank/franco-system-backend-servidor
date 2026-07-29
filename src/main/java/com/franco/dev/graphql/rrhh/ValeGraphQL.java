package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.graphql.rrhh.input.ValeInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.MotivoValeService;
import com.franco.dev.service.rrhh.ValeService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToLocalDate;

@Component
public class ValeGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ValeService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private MotivoValeService motivoValeService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Vale> vale(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<Vale> valesPorFuncionario(Long funcionarioId) {
        seg.requireVer();
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<Vale> valesPorEstado(ValeEstado estado) {
        seg.requireVer();
        return service.findByEstado(estado);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<Vale> valesPage(int page, int size, Long funcionarioId, ValeEstado estado,
                                String desde, String hasta) {
        seg.requireVer();
        return service.findPage(funcionarioId, estado, stringToLocalDate(desde), stringToLocalDate(hasta),
                PageRequest.of(page, size));
    }

    public Vale saveVale(ValeInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        Vale e = mapInput(input, input.getId() != null
                ? service.findById(input.getId()).orElse(new Vale())
                : new Vale());
        return service.save(e);
    }

    public Vale confirmarVale(Long id, Long cajaVirtualId, Long autorizadoPorId) {
        seg.requireAnyRole(seg.APROBAR);
        return service.confirmar(id, cajaVirtualId, autorizadoPorId);
    }

    public Vale crearValeConfirmado(ValeInput input, Long cajaVirtualId, Long autorizadoPorId) {
        seg.requireAnyRole(seg.APROBAR);
        Vale e = mapInput(input, new Vale());
        return service.crearConfirmado(e, cajaVirtualId, autorizadoPorId);
    }

    public Vale anularVale(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.anular(id);
    }

    private Vale mapInput(ValeInput input, Vale e) {
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getMotivoId() != null)
            e.setMotivo(motivoValeService.findById(input.getMotivoId()).orElse(null));
        e.setMonto(input.getMonto());
        if (input.getMonedaId() != null)
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        if (input.getEstado() != null) e.setEstado(input.getEstado());
        if (input.getEsAdelanto() != null) e.setEsAdelanto(input.getEsAdelanto());
        e.setObservacion(input.getObservacion());
        e.setComprobanteUrl(input.getComprobanteUrl());
        if (input.getAutorizadoPorId() != null)
            e.setAutorizadoPor(usuarioService.findById(input.getAutorizadoPorId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return e;
    }
}
