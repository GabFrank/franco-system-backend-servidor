package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.enums.MotivoEgreso;
import com.franco.dev.service.rrhh.LiquidacionFinalService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class LiquidacionFinalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LiquidacionFinalService service;

    // ----- Queries -----

    public Optional<LiquidacionFinal> liquidacionFinal(Long id) {
        return service.findById(id);
    }

    public List<LiquidacionFinal> liquidacionesFinalesPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<LiquidacionFinalItem> liquidacionFinalItems(Long liquidacionFinalId) {
        return service.findItems(liquidacionFinalId);
    }

    // ----- Mutations -----

    public LiquidacionFinal generarLiquidacionFinal(Long funcionarioId, MotivoEgreso motivoEgreso, String fechaEgreso, Long monedaId) {
        return service.generarBorrador(funcionarioId, motivoEgreso, parseFecha(fechaEgreso), monedaId);
    }

    public LiquidacionFinal aprobarLiquidacionFinal(Long id, Long aprobadoPorId) {
        return service.aprobar(id, aprobadoPorId);
    }

    public LiquidacionFinal volverBorradorLiquidacionFinal(Long id) {
        return service.volverBorrador(id);
    }

    public LiquidacionFinal pagarLiquidacionFinal(Long id, Long cajaVirtualId) {
        return service.pagar(id, cajaVirtualId);
    }

    public LiquidacionFinal anularLiquidacionFinal(Long id) {
        return service.anular(id);
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        return stringToDate(s) != null ? stringToDate(s).toLocalDate() : null;
    }
}
