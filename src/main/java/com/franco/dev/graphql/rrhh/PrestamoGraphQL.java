package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.graphql.rrhh.input.PrestamoInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.PrestamoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class PrestamoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PrestamoService service;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Prestamo> prestamo(Long id) {
        return service.findById(id);
    }

    public List<Prestamo> prestamosPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<Prestamo> prestamosPorEstado(PrestamoEstado estado) {
        return service.findByEstado(estado);
    }

    public List<PrestamoCuota> prestamoCuotas(Long prestamoId) {
        return service.findCuotas(prestamoId);
    }

    public Prestamo crearPrestamo(PrestamoInput input, Long cajaVirtualId) {
        Prestamo p = new Prestamo();
        if (input.getFuncionarioId() != null)
            p.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        p.setDescripcion(input.getDescripcion());
        p.setMontoTotal(input.getMontoTotal());
        if (input.getMonedaId() != null)
            p.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        if (input.getFechaInicio() != null && stringToDate(input.getFechaInicio()) != null)
            p.setFechaInicio(stringToDate(input.getFechaInicio()).toLocalDate());
        p.setCantidadCuotas(input.getCantidadCuotas());
        p.setObservacion(input.getObservacion());
        if (input.getUsuarioId() != null)
            p.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.crearConDesembolso(p, cajaVirtualId);
    }

    public PrestamoCuota cobrarCuota(Long cuotaId, Long cajaVirtualId, BigDecimal montoPago) {
        return service.cobrarCuota(cuotaId, cajaVirtualId, montoPago);
    }
}
