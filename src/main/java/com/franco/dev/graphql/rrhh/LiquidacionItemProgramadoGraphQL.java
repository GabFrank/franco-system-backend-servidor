package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.service.rrhh.LiquidacionItemProgramadoService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class LiquidacionItemProgramadoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LiquidacionItemProgramadoService service;

    @Autowired
    private RrhhSecurityService seg;

    public List<LiquidacionItemProgramado> itemsProgramadosPorFuncionario(Long funcionarioId,
                                                                          LiquidacionItemProgramadoEstado estado) {
        seg.requireVer();
        return service.findPorFuncionario(funcionarioId, estado);
    }

    /** Mismo rol que agregar un ítem a la liquidación. */
    public LiquidacionItemProgramado programarItemLiquidacion(Long liquidacionId, String periodo, String descripcion,
                                                              BigDecimal monto, LiquidacionItemTipo tipo,
                                                              Long liquidacionConceptoId) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.programar(liquidacionId, periodo, descripcion, monto, tipo, liquidacionConceptoId,
                seg.currentUsuario());
    }

    public LiquidacionItemProgramado anularItemProgramado(Long id) {
        seg.requireAnyRole(seg.LIQUIDAR);
        return service.anular(id);
    }
}
