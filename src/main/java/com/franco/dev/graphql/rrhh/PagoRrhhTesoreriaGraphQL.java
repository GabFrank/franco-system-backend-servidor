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
    private final com.franco.dev.service.rrhh.RrhhAlcanceService alcance;

    public List<PagoRrhhPendienteDto> liquidacionesPendientesPago() {
        alcance.requireVer();
        return soloEnAlcance(service.listarLiquidacionesPendientes());
    }

    public List<PagoRrhhPendienteDto> finiquitosPendientesPago() {
        alcance.requireVer();
        return soloEnAlcance(service.listarFiniquitosPendientes());
    }

    /** El rol acotado RRHH SUCURSAL ve pendientes solo de funcionarios de sus sucursales. */
    private List<PagoRrhhPendienteDto> soloEnAlcance(List<PagoRrhhPendienteDto> pendientes) {
        if (!alcance.veAcotado()) return pendientes;
        List<PagoRrhhPendienteDto> out = new ArrayList<>();
        for (PagoRrhhPendienteDto d : pendientes) {
            if (alcance.enAlcance(d.getFuncionario())) out.add(d);
        }
        return out;
    }

    /**
     * El rol acotado paga liquidaciones y finiquitos de sus sucursales, en efectivo y desde una caja que
     * puede mover. Se recorre todo el pedido antes de pagar: un rechazo no deja nada asentado.
     */
    private void exigirPagoAcotado(List<PagoRrhhTesoreriaService.PagoRrhhConLineas> pagos) {
        for (PagoRrhhTesoreriaService.PagoRrhhConLineas p : pagos) {
            if (p.getConcepto() == PagoRrhhTesoreriaService.ConceptoRrhh.LIQUIDACION) {
                alcance.exigirLiquidacion(p.getDocumentoId());
            } else if (p.getConcepto() == PagoRrhhTesoreriaService.ConceptoRrhh.FINIQUITO) {
                alcance.exigirFiniquito(p.getDocumentoId());
            } else {
                throw new graphql.GraphQLException("No autorizado: con tu rol solo podés pagar liquidaciones y finiquitos.");
            }
            if (p.getLineas() == null) continue;
            for (com.franco.dev.service.financiero.PagoProveedorService.LineaPago l : p.getLineas()) {
                if (l.getFuente() != com.franco.dev.domain.financiero.enums.FuentePago.CAJA_MAYOR || l.getCajaVirtualId() == null) {
                    throw new graphql.GraphQLException("No autorizado: con tu rol solo podés pagar en efectivo desde tu caja.");
                }
                alcance.exigirCajaParaPagar(l.getCajaVirtualId());
            }
        }
        service.exigirEfectivoEnLaMonedaDelDocumento(pagos);
    }

    public List<PagoRrhhPendienteDto> aguinaldosPendientesPago() {
        seg.requireVer();
        return service.listarAguinaldosPendientes();
    }

    /** Paga documentos de RRHH (caja mayor / banco / cheque). El pago parcial esta prohibido. */
    public Pago pagarRrhhMixto(List<PagoRrhhConLineasWrapper> pagos) {
        alcance.requirePagar();
        List<PagoRrhhTesoreriaService.PagoRrhhConLineas> ls = new ArrayList<>();
        for (PagoRrhhConLineasWrapper w : pagos) {
            PagoRrhhTesoreriaService.PagoRrhhConLineas p = new PagoRrhhTesoreriaService.PagoRrhhConLineas();
            p.setConcepto(w.getConcepto());
            p.setDocumentoId(w.getDocumentoId());
            p.setLineas(PagoProveedorGraphQL.mapLineas(w.getLineas()));
            ls.add(p);
        }
        if (alcance.pagaAcotado()) exigirPagoAcotado(ls);
        return service.pagarRrhhMixto(ls, seg.currentUsuario());
    }

    @Data
    public static class PagoRrhhConLineasWrapper {
        private PagoRrhhTesoreriaService.ConceptoRrhh concepto;
        private Long documentoId;
        private List<PagoProveedorGraphQL.LineaPagoInputWrapper> lineas;
    }
}
