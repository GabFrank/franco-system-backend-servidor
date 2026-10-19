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

    @Autowired
    private com.franco.dev.service.financiero.AnulacionPagoRrhhService anulacionPagoRrhhService;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    // Rol acotado RRHH SUCURSAL: ve, genera y edita solo finiquitos de funcionarios de sus sucursales.
    @Autowired
    private com.franco.dev.service.rrhh.RrhhAlcanceService alcance;

    // ----- Queries -----

    public Optional<LiquidacionFinal> liquidacionFinal(Long id) {
        alcance.requireVerFiniquito(id);
        return service.findById(id);
    }

    public List<LiquidacionFinal> liquidacionesFinalesPorFuncionario(Long funcionarioId) {
        alcance.requireVer();
        if (alcance.veAcotado()) alcance.exigirFuncionario(funcionarioId);
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<LiquidacionFinalItem> liquidacionFinalItems(Long liquidacionFinalId) {
        alcance.requireVerFiniquito(liquidacionFinalId);
        return service.findItems(liquidacionFinalId);
    }

    public LiquidacionFinalPreview previewLiquidacionFinal(Long funcionarioId, String fechaEgreso) {
        alcance.requireVer();
        if (alcance.veAcotado()) alcance.exigirFuncionario(funcionarioId);
        return service.previewDefaults(funcionarioId, parseFecha(fechaEgreso));
    }

    // ----- Mutations -----

    public LiquidacionFinal generarLiquidacionFinal(LiquidacionFinalGenerarInput input) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirFuncionario(input != null ? input.getFuncionarioId() : null);
        return service.generarBorrador(input);
    }

    public LiquidacionFinal aprobarLiquidacionFinal(Long id, Long aprobadoPorId) {
        seg.requireAnyRole(seg.APROBAR);
        return service.aprobar(id, aprobadoPorId);
    }

    public LiquidacionFinal volverBorradorLiquidacionFinal(Long id) {
        seg.requireAnyRole(seg.LIQUIDAR, seg.APROBAR);
        return service.volverBorrador(id);
    }

    public LiquidacionFinal pagarLiquidacionFinal(Long id, Long cajaVirtualId) {
        seg.requireAnyRole(seg.PAGAR);
        return service.pagar(id, cajaVirtualId);
    }

    public LiquidacionFinal anularLiquidacionFinal(Long id) {
        seg.requireAnyRole(seg.PAGAR);
        // Pagado desde tesoreria: se anula junto con su pago (pide ademas el rol de tesoreria).
        return anulacionPagoRrhhService.anularFiniquito(id);
    }

    public LiquidacionFinalItem agregarItemLiquidacionFinal(Long liquidacionFinalId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirFiniquito(liquidacionFinalId);
        return service.agregarItemManual(liquidacionFinalId, descripcion, monto, tipo);
    }

    public LiquidacionFinalItem editarItemLiquidacionFinal(Long itemId, String descripcion, BigDecimal monto, LiquidacionItemTipo tipo, Long usuarioId) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirItemDeFiniquito(itemId);
        return service.editarItem(itemId, descripcion, monto, tipo, usuarioId);
    }

    public Boolean eliminarItemLiquidacionFinal(Long itemId) {
        alcance.requireLiquidar();
        if (alcance.liquidaAcotado()) alcance.exigirItemDeFiniquito(itemId);
        return service.eliminarItem(itemId);
    }

    private LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return null;
        return stringToDate(s) != null ? stringToDate(s).toLocalDate() : null;
    }
}
