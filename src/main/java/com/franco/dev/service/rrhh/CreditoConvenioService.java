package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.service.personas.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cobro en planilla de las compras a credito del funcionario (crédito por convenio).
 * Compartido por la liquidacion mensual (cuotas vencidas) y el finiquito (todas las
 * cuotas impagas). El saldo ya cobrado de cada cuota se rastrea en RRHH (suma de los
 * items de liquidacion no anulados), sin tocar el schema financiero (replicado).
 *
 * Puente: no crea Cobro ni movimiento de caja — ese asiento formal lo hara el modulo
 * de Tesoreria (Caja Mayor/Virtual). Aca solo se marca la cuota como cobrada-por-planilla
 * (via el item) y, cuando la venta queda 100% saldada, se la finaliza.
 */
@Service
public class CreditoConvenioService {

    @Autowired private VentaCreditoService ventaCreditoService;
    @Autowired private ClienteService clienteService;
    @Autowired private LiquidacionItemRepository mensualItemRepository;
    @Autowired private LiquidacionFinalItemRepository finalItemRepository;

    /** Una cuota a cobrar en la liquidacion, con el monto (posiblemente parcial). */
    public static class CobroCuota {
        public final VentaCreditoCuota cuota;
        public final VentaCredito ventaCredito;
        public final BigDecimal monto;
        public final boolean parcial;
        public CobroCuota(VentaCreditoCuota cuota, BigDecimal monto, boolean parcial) {
            this.cuota = cuota;
            this.ventaCredito = cuota != null ? cuota.getVentaCredito() : null;
            this.monto = monto;
            this.parcial = parcial;
        }
    }

    /**
     * Planifica el cobro de convenio hasta `disponible`, de la cuota mas vieja a la mas
     * nueva, cobrando el remanente de cada una (parcial si no alcanza). No emite items:
     * devuelve el plan para que el service llamador arme los items.
     *
     * @param personaId   persona del funcionario (se resuelve su Cliente).
     * @param hasta       solo cuotas vencidas hasta esta fecha (mensual); null = todas (finiquito).
     * @param disponible  tope del neto disponible; si <= 0 no se cobra nada.
     * @param exMensualId liquidacion mensual a excluir del saldo ya cobrado (la en curso).
     * @param exFinalId   liquidacion final a excluir del saldo ya cobrado (la en curso).
     */
    public List<CobroCuota> planificar(Long personaId, LocalDateTime hasta, BigDecimal disponible,
                                       Long exMensualId, Long exFinalId) {
        List<CobroCuota> plan = new ArrayList<>();
        if (personaId == null || disponible == null || disponible.signum() <= 0) return plan;
        Cliente cli = clienteService.findByPersonaId(personaId);
        if (cli == null) return plan;
        BigDecimal restante = disponible;
        for (VentaCreditoCuota c : ventaCreditoService.cuotasImpagasDeCliente(cli.getId(), hasta)) {
            if (restante.signum() <= 0) break;
            BigDecimal remanente = remanenteCuota(c, exMensualId, exFinalId);
            if (remanente.signum() <= 0) continue;
            BigDecimal aCobrar = remanente.min(restante);
            plan.add(new CobroCuota(c, aCobrar, aCobrar.compareTo(remanente) < 0));
            restante = restante.subtract(aCobrar);
        }
        return plan;
    }

    /** Remanente de una cuota = valor − ya cobrado (mensual + finiquito, no anulados). */
    public BigDecimal remanenteCuota(VentaCreditoCuota c, Long exMensualId, Long exFinalId) {
        if (c == null) return BigDecimal.ZERO;
        BigDecimal valor = c.getValor() != null ? BigDecimal.valueOf(c.getValor()) : BigDecimal.ZERO;
        BigDecimal cobrado = nz(mensualItemRepository.sumConvenioCobrado(c.getId(), c.getSucursalId(), exMensualId))
                .add(nz(finalItemRepository.sumConvenioCobrado(c.getId(), c.getSucursalId(), exFinalId)));
        return valor.subtract(cobrado);
    }

    /**
     * Regla unificada de estado del VentaCredito tras pagar/anular:
     * - si no le queda remanente en ninguna cuota → FINALIZADO;
     * - si le queda y hay estado previo → se restaura (revierte el FINALIZADO).
     * En el pago se llama con estadoPrevio=null (solo puede finalizar). En la anulacion
     * se excluye la liquidacion que se esta anulando para recalcular el remanente real.
     */
    public void reconciliarEstado(VentaCredito vc, String estadoPrevio, Long exMensualId, Long exFinalId) {
        if (vc == null) return;
        boolean saldado = true;
        for (VentaCreditoCuota c : ventaCreditoService.cuotasDeVenta(vc)) {
            if (c.getCobro() != null) continue;                 // pagada en el POS
            if (Boolean.FALSE.equals(c.getActivo())) continue;
            if (remanenteCuota(c, exMensualId, exFinalId).signum() > 0) { saldado = false; break; }
        }
        if (saldado) {
            ventaCreditoService.finalizarPorCobro(vc);
        } else if (estadoPrevio != null) {
            ventaCreditoService.revertirFinalizacion(vc, EstadoVentaCredito.valueOf(estadoPrevio));
        }
    }

    /**
     * Resuelve la cuota (id + sucursal) → su VentaCredito y reconcilia su estado.
     * Lo usan los `aplicarEfectosCruzados` de la mensual y del finiquito.
     */
    public void reconciliarPorCuota(Long cuotaId, Long sucId, String estadoPrevio,
                                    Long exMensualId, Long exFinalId) {
        VentaCreditoCuota c = ventaCreditoService.cuota(cuotaId, sucId);
        if (c == null) return;
        reconciliarEstado(c.getVentaCredito(), estadoPrevio, exMensualId, exFinalId);
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
