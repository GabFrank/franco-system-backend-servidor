package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.AcreditacionPos;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.financiero.enums.EstadoAcreditacionPos;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.AcreditacionPosRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Acreditación de ventas con tarjeta a la cuenta bancaria de la terminal POS.
 * Crea la acreditación PENDIENTE (comisión + fecha esperada); el scheduler acredita
 * las vencidas; la verificación manual ajusta la diferencia contra el extracto.
 */
@Service
@AllArgsConstructor
public class AcreditacionPosService {

    private final AcreditacionPosRepository repository;
    private final BancoLedgerService bancoLedgerService;

    /** Crea una acreditación PENDIENTE a partir de la venta con tarjeta y la terminal. */
    @Transactional
    public AcreditacionPos crear(TerminalPos terminal, CuentaBancaria cuenta, BigDecimal montoOriginal,
                                 Long ventaId, Usuario usuario) {
        if (montoOriginal == null || montoOriginal.signum() <= 0) throw new GraphQLException("Monto inválido");
        BigDecimal pct = terminal != null && terminal.getPorcentajeComision() != null
                ? terminal.getPorcentajeComision() : BigDecimal.ZERO;
        BigDecimal comision = montoOriginal.multiply(pct).movePointLeft(2);
        BigDecimal esperado = montoOriginal.subtract(comision);
        int minutos = terminal != null && terminal.getMinutosAcreditacion() != null ? terminal.getMinutosAcreditacion() : 0;

        AcreditacionPos a = new AcreditacionPos();
        a.setTerminalPos(terminal);
        a.setCuentaBancaria(cuenta != null ? cuenta : (terminal != null ? terminal.getCuentaBancaria() : null));
        a.setMontoOriginal(montoOriginal);
        a.setMontoComision(comision);
        a.setMontoEsperado(esperado);
        LocalDateTime ahora = LocalDateTime.now();
        a.setFechaTransaccion(ahora);
        a.setFechaEsperadaAcreditacion(ahora.plusMinutes(minutos));
        a.setEstado(EstadoAcreditacionPos.PENDIENTE);
        a.setVentaId(ventaId);
        a.setUsuario(usuario);
        return repository.save(a);
    }

    /** Acredita las pendientes vencidas (invocado por el scheduler o manualmente). Devuelve cuántas. */
    @Transactional
    public int procesarPendientes(int lote) {
        List<AcreditacionPos> pendientes = repository.findByEstadoAndFechaEsperadaAcreditacionLessThanEqual(
                EstadoAcreditacionPos.PENDIENTE, LocalDateTime.now(), PageRequest.of(0, lote));
        int n = 0;
        for (AcreditacionPos a : pendientes) {
            if (acreditar(a.getId())) n++;
        }
        return n;
    }

    /** Acredita una pendiente: entra el monto esperado a la cuenta. Idempotente por estado. */
    @Transactional
    public boolean acreditar(Long id) {
        AcreditacionPos a = repository.lockById(id).orElse(null);
        if (a == null || a.getEstado() != EstadoAcreditacionPos.PENDIENTE) return false;
        if (a.getCuentaBancaria() == null) return false;
        MovimientoBancario mov = bancoLedgerService.registrar(a.getCuentaBancaria().getId(),
                MovimientoBancarioTipo.ACREDITACION_POS, a.getMontoEsperado(),
                "Acreditación POS #" + a.getId(), "ACREDITACION_POS", a.getId(), null);
        a.setMovimientoBancarioId(mov != null ? mov.getId() : null);
        a.setFechaAcreditacionReal(LocalDateTime.now());
        a.setEstado(EstadoAcreditacionPos.ACREDITADO_AUTO);
        repository.save(a);
        return true;
    }

    /** Verificación manual contra extracto: ajusta la diferencia si el monto real difiere del esperado. */
    @Transactional
    public AcreditacionPos verificar(Long id, BigDecimal montoReal, Usuario usuario) {
        AcreditacionPos a = repository.lockById(id).orElseThrow(
                () -> new GraphQLException("Acreditación no encontrada: " + id));
        if (a.getCuentaBancaria() == null) {
            throw new GraphQLException("La acreditación no tiene cuenta bancaria asociada");
        }
        if (a.getEstado() == EstadoAcreditacionPos.VERIFICADO || a.getEstado() == EstadoAcreditacionPos.CON_DIFERENCIA) {
            throw new GraphQLException("La acreditación ya fue verificada");
        }
        BigDecimal esperado = a.getMontoEsperado() != null ? a.getMontoEsperado() : BigDecimal.ZERO;
        if (a.getEstado() == EstadoAcreditacionPos.ACREDITADO_AUTO) {
            // Ya se acreditó el esperado; ajustar solo la diferencia.
            BigDecimal dif = montoReal.subtract(esperado);
            if (dif.signum() != 0) {
                MovimientoBancarioTipo tipo = dif.signum() > 0
                        ? MovimientoBancarioTipo.AJUSTE_POSITIVO : MovimientoBancarioTipo.AJUSTE_NEGATIVO;
                bancoLedgerService.registrar(a.getCuentaBancaria().getId(), tipo, dif.abs(),
                        "Ajuste verificación POS #" + a.getId(), "ACREDITACION_POS", a.getId(), usuario);
            }
        } else {
            // Aún no acreditada: acreditar el monto real completo.
            bancoLedgerService.registrar(a.getCuentaBancaria().getId(), MovimientoBancarioTipo.ACREDITACION_POS,
                    montoReal, "Acreditación POS verificada #" + a.getId(), "ACREDITACION_POS", a.getId(), usuario);
        }
        a.setMontoAcreditado(montoReal);
        a.setDiferencia(montoReal.subtract(esperado));
        a.setEstado(a.getDiferencia().signum() == 0 ? EstadoAcreditacionPos.VERIFICADO : EstadoAcreditacionPos.CON_DIFERENCIA);
        return repository.save(a);
    }
}
