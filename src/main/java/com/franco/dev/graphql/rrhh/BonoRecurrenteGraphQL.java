package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.graphql.rrhh.input.BonoRecurrenteInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.BonoRecurrenteService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Optional;

@Component
public class BonoRecurrenteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private BonoRecurrenteService service;

    @Autowired
    private RrhhSecurityService seg;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<BonoRecurrente> bonoRecurrente(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<BonoRecurrente> bonosRecurrentesPage(int page, int size, Long funcionarioId, Boolean activo) {
        seg.requireVer();
        return service.findPage(funcionarioId, activo, PageRequest.of(page, size));
    }

    public BonoRecurrente saveBonoRecurrente(BonoRecurrenteInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        BonoRecurrente e = input.getId() != null
                ? service.findById(input.getId()).orElse(new BonoRecurrente())
                : new BonoRecurrente();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        e.setTipo(input.getTipo());
        e.setMonto(input.getMonto());
        e.setFrecuencia(input.getFrecuencia());
        e.setMotivo(input.getMotivo());
        if (input.getActivo() != null) e.setActivo(input.getActivo());
        if (input.getAutorizadoPorId() != null)
            e.setAutorizadoPor(usuarioService.findById(input.getAutorizadoPorId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));

        BonoRecurrente guardado = service.save(e);

        // Alta a mitad de mes: se genera el bono del mes corriente por la misma
        // ruta que usa el job, no por una copia. Si el job ya lo genero, el
        // chequeo de idempotencia de generarUno() lo deja pasar sin duplicar.
        service.generarUno(guardado.getId(), YearMonth.now());

        return guardado;
    }

    public BonoRecurrente cambiarEstadoBonoRecurrente(Long id, Boolean activo) {
        seg.requireAnyRole(seg.GESTIONAR);
        BonoRecurrente e = service.findById(id)
                .orElseThrow(() -> new GraphQLException("Bono recurrente no encontrado"));
        e.setActivo(Boolean.TRUE.equals(activo));
        return service.save(e);
    }
}
