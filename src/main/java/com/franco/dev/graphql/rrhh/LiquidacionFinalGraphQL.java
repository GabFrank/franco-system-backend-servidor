package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.graphql.rrhh.input.LiquidacionFinalGenerarInput;
import com.franco.dev.service.rrhh.dto.LiquidacionFinalPreview;

import java.math.BigDecimal;
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

    public LiquidacionFinalPreview previewLiquidacionFinal(Long funcionarioId, String fechaEgreso) {
        return service.previewDefaults(funcionarioId, parseFecha(fechaEgreso));
    }

    // ----- Mutations -----

    public LiquidacionFinal generarLiquidacionFinal(LiquidacionFinalGenerarInput input) {
        return service.generarBorrador(input);
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

    public LiquidacionFinalItem agregarItemLiquidacionFinal(Long liquidacionFinalId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo) {
        return service.agregarItemManual(liquidacionFinalId, descripcion, monto, tipo);
    }

    public LiquidacionFinalItem editarItemLiquidacionFinal(Long itemId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo, Long usuarioId) {
        return service.editarItem(itemId, descripcion, monto, tipo, usuarioId);
    }

    public Boolean eliminarItemLiquidacionFinal(Long itemId) {
        return service.eliminarItem(itemId);
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        return stringToDate(s) != null ? stringToDate(s).toLocalDate() : null;
    }
}
