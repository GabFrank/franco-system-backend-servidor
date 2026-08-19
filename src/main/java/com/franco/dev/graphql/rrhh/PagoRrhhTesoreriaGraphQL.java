package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.graphql.financiero.PagoProveedorGraphQL;
import com.franco.dev.service.financiero.PagoRrhhTesoreriaService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import com.franco.dev.service.rrhh.dto.PagoRrhhPendienteDto;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pago de liquidacion mensual, finiquito y aguinaldo desde el hub de egresos de la Caja
 * Mayor. Hermano de {@link ValeTesoreriaGraphQL}, mismo puente y mismo motor de pago.
 *
 * <p>Seguridad: paridad exacta con los pagos que ya existian desde las pantallas de RRHH
 * ({@code pagarLiquidacion} / {@code pagarLiquidacionFinal} / {@code pagarAguinaldo} exigen
 * {@code RRHH PAGAR}). Listar exige lectura de RRHH. No amplia ni restringe privilegios
 * respecto de lo que la gente ya podia hacer.</p>
 */
@Component
@AllArgsConstructor
public class PagoRrhhTesoreriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final PagoRrhhTesoreriaService service;
    private final RrhhSecurityService seg;

    public List<PagoRrhhPendienteDto> liquidacionesPendientesPago() {
        seg.requireVer();
        return service.listarLiquidacionesPendientes();
    }

    public List<PagoRrhhPendienteDto> finiquitosPendientesPago() {
        seg.requireVer();
        return service.listarFiniquitosPendientes();
    }

    public List<PagoRrhhPendienteDto> aguinaldosPendientesPago() {
        seg.requireVer();
        return service.listarAguinaldosPendientes();
    }

    /** Paga documentos de RRHH (caja mayor / banco / cheque). El pago parcial esta prohibido. */
    public Pago pagarRrhhMixto(List<PagoRrhhConLineasWrapper> pagos) {
        seg.requireAnyRole(seg.PAGAR);
        List<PagoRrhhTesoreriaService.PagoRrhhConLineas> ls = new ArrayList<>();
        for (PagoRrhhConLineasWrapper w : pagos) {
            PagoRrhhTesoreriaService.PagoRrhhConLineas p = new PagoRrhhTesoreriaService.PagoRrhhConLineas();
            p.setConcepto(w.getConcepto());
            p.setDocumentoId(w.getDocumentoId());
            p.setLineas(PagoProveedorGraphQL.mapLineas(w.getLineas()));
            ls.add(p);
        }
        return service.pagarRrhhMixto(ls, seg.currentUsuario());
    }

    @Data
    public static class PagoRrhhConLineasWrapper {
        private PagoRrhhTesoreriaService.ConceptoRrhh concepto;
        private Long documentoId;
        private List<PagoProveedorGraphQL.LineaPagoInputWrapper> lineas;
    }
}
