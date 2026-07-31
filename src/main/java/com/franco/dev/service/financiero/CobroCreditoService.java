package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.MovimientoClienteTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * CPC — cobro de cuotas de venta a crédito desde Tesorería central.
 *
 * <p>Por DA8, el cobro en <b>efectivo</b> es exclusivo del POS de la filial (que
 * postea a su caja física). Tesorería central cobra los canales que el POS no
 * maneja: <b>cuenta bancaria</b> (y cheque en F7). Cada cobro es atómico y de doble
 * ledger: acredita la cuenta bancaria + registra un PAGO en el libro del cliente
 * (baja {@code Cliente.saldoActual}), con soporte de cobro parcial y tolerancia de
 * redondeo. Si todas las cuotas quedan cobradas, la venta pasa a FINALIZADO.
 */
@Service
@AllArgsConstructor
public class CobroCreditoService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final VentaCreditoService ventaCreditoService;
    private final VentaCreditoCuotaService ventaCreditoCuotaService;
    private final com.franco.dev.repository.financiero.VentaCreditoCuotaRepository ventaCreditoCuotaRepository;
    private final BancoLedgerService bancoLedgerService;
    private final ClienteCuentaService clienteCuentaService;

    /**
     * Cobra (total o parcialmente) una cuota de venta a crédito acreditando a una cuenta bancaria.
     *
     * @param cuotaId          id de la cuota
     * @param sucId            sucursal de la cuota (PK compuesta)
     * @param montoCobrar      monto a imputar a la cuota (en la moneda de la deuda)
     * @param cuentaBancariaId cuenta bancaria donde entra el dinero
     * @param montoBanco       monto acreditado a la cuenta (== montoCobrar si misma moneda; convertido si no)
     * @param cotizacion       cotización aplicada si la cuenta es de otra moneda (o null/1)
     */
    @Transactional
    public VentaCreditoCuota cobrarCuotaBanco(Long cuotaId, Long sucId, BigDecimal montoCobrar,
                                              Long cuentaBancariaId, BigDecimal montoBanco,
                                              BigDecimal cotizacion, Usuario usuario) {
        // Lock pesimista de la cuota: serializa cobros parciales concurrentes sobre la misma cuota.
        VentaCreditoCuota cuota = ventaCreditoCuotaRepository.lockByIdAndSucursalId(cuotaId, sucId).orElse(null);
        if (cuota == null) throw new GraphQLException("Cuota no encontrada: " + cuotaId + "/" + sucId);
        if ("COBRADO".equals(cuota.getEstadoCobro()) || "CANCELADO".equals(cuota.getEstadoCobro())) {
            throw new GraphQLException("La cuota ya está " + cuota.getEstadoCobro());
        }
        if (cuentaBancariaId == null) throw new GraphQLException("Debe indicar la cuenta bancaria de cobro");
        if (montoCobrar == null || montoCobrar.signum() <= 0) throw new GraphQLException("Monto a cobrar inválido");

        BigDecimal valor = BigDecimal.valueOf(cuota.getValor() != null ? cuota.getValor() : 0.0);
        BigDecimal cobrado = cuota.getMontoCobrado() != null ? cuota.getMontoCobrado() : BigDecimal.ZERO;
        BigDecimal restante = valor.subtract(cobrado);
        if (montoCobrar.subtract(restante).compareTo(TOLERANCIA) > 0) {
            throw new GraphQLException("El monto excede el saldo de la cuota (" + restante + ")");
        }

        // 1. Acreditar la cuenta bancaria (ledger de banco)
        BigDecimal aBanco = montoBanco != null ? montoBanco : montoCobrar;
        MovimientoBancario movBanco = bancoLedgerService.registrar(cuentaBancariaId,
                MovimientoBancarioTipo.ENTRADA_MANUAL, aBanco,
                "Cobro cuota crédito #" + cuotaId, OrigenMovimientoTipo.VENTA_CREDITO_COBRO.name(), cuotaId, usuario);

        // 2. Actualizar la cuota
        BigDecimal nuevoCobrado = cobrado.add(montoCobrar);
        cuota.setMontoCobrado(nuevoCobrado);
        boolean completa = nuevoCobrado.subtract(valor).abs().compareTo(TOLERANCIA) <= 0
                || nuevoCobrado.compareTo(valor) >= 0;
        cuota.setEstadoCobro(completa ? "COBRADO" : "PARCIAL");
        cuota.setParcial(!completa);
        ventaCreditoCuotaService.save(cuota);

        // 3. Libro del cliente (PAGO) — doble ledger
        VentaCredito vc = cuota.getVentaCredito();
        if (vc != null && vc.getCliente() != null) {
            MovimientoCliente plantilla = new MovimientoCliente();
            plantilla.setVentaCreditoId(vc.getId());
            plantilla.setVentaCreditoCuotaId(cuota.getId());
            plantilla.setMovimientoBancarioId(movBanco != null ? movBanco.getId() : null);
            plantilla.setCotizacion(cotizacion);
            plantilla.setDescripcion("Cobro cuota crédito #" + cuotaId + " (banco)");
            clienteCuentaService.registrar(vc.getCliente().getId(), MovimientoClienteTipo.PAGO, montoCobrar, plantilla, usuario);
        }

        // 4. ¿Todas las cuotas cobradas? → finalizar la venta
        if (vc != null && completa && todasCobradas(vc)) {
            ventaCreditoService.finalizarPorCobro(vc);
        }
        return cuota;
    }

    private boolean todasCobradas(VentaCredito vc) {
        List<VentaCreditoCuota> cuotas = ventaCreditoService.cuotasDeVenta(vc);
        for (VentaCreditoCuota c : cuotas) {
            if (Boolean.TRUE.equals(c.getActivo() == null ? Boolean.TRUE : c.getActivo())) {
                if (!"COBRADO".equals(c.getEstadoCobro()) && !"CANCELADO".equals(c.getEstadoCobro())) return false;
            }
        }
        return true;
    }
}
