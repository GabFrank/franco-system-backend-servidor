package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-model de tesorería: consolida los saldos de efectivo (todas las cajas mayor)
 * y de bancos por moneda, dando la vista unificada de liquidez (presentación; los
 * ledgers de caja y banco siguen separados — modelo cash-only).
 */
@Service
@AllArgsConstructor
public class TesoreriaReporteService {

    private final CajaVirtualSaldoRepository cajaSaldoRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final SolicitudPagoRepository solicitudPagoRepository;
    private final ChequeRepository chequeRepository;

    @Data
    public static class SaldoPorMoneda {
        private Long monedaId;
        private String moneda;
        private BigDecimal efectivo = BigDecimal.ZERO;
        private BigDecimal banco = BigDecimal.ZERO;
        private BigDecimal bancoReservado = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;
    }

    /** Saldo consolidado (efectivo + banco) por moneda. */
    public List<SaldoPorMoneda> saldoConsolidado() {
        Map<Long, SaldoPorMoneda> map = new LinkedHashMap<>();
        for (Object[] row : cajaSaldoRepository.saldoConsolidadoPorMoneda()) {
            SaldoPorMoneda s = get(map, (Long) row[0], (String) row[1]);
            s.setEfectivo(toBig(row[2]));
        }
        for (Object[] row : cuentaBancariaRepository.saldoBancarioPorMoneda()) {
            SaldoPorMoneda s = get(map, (Long) row[0], (String) row[1]);
            s.setBanco(toBig(row[2]));
            s.setBancoReservado(toBig(row[3]));
        }
        List<SaldoPorMoneda> out = new ArrayList<>(map.values());
        for (SaldoPorMoneda s : out) s.setTotal(s.getEfectivo().add(s.getBanco()));
        return out;
    }

    private SaldoPorMoneda get(Map<Long, SaldoPorMoneda> map, Long monedaId, String moneda) {
        return map.computeIfAbsent(monedaId, k -> {
            SaldoPorMoneda s = new SaldoPorMoneda();
            s.setMonedaId(monedaId);
            s.setMoneda(moneda);
            return s;
        });
    }

    private static BigDecimal toBig(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }

    // ---- Vencimientos (CPP + cheques diferidos) ----

    @Data
    public static class Vencimiento {
        private String tipo;          // CPP | CHEQUE
        private Long referenciaId;
        private String descripcion;
        private BigDecimal monto;
        private LocalDateTime fecha;
        private long diasRestantes;   // negativo = vencido
    }

    /** Próximos vencimientos (CPP pendientes/parciales + cheques diferidos) dentro de {@code dias}. */
    public List<Vencimiento> proximosVencimientos(int dias) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime hasta = ahora.plusDays(dias);
        List<Vencimiento> out = new ArrayList<>();

        for (SolicitudPago sp : solicitudPagoRepository.findByEstadoIn(SolicitudPagoEstado.DEUDA_ABIERTA)) {
            LocalDateTime f = sp.getFechaPagoPropuesta();
            if (f == null || f.isAfter(hasta)) continue;
            BigDecimal total = BigDecimal.valueOf(sp.getMontoTotal() != null ? sp.getMontoTotal() : 0.0);
            BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
            Vencimiento v = new Vencimiento();
            v.setTipo("CPP"); v.setReferenciaId(sp.getId());
            v.setDescripcion("Solicitud #" + sp.getId());
            v.setMonto(total.subtract(pagado)); v.setFecha(f);
            v.setDiasRestantes(ChronoUnit.DAYS.between(ahora.toLocalDate(), f.toLocalDate()));
            out.add(v);
        }
        for (Cheque ch : chequeRepository.findByEstado(EstadoCheque.DIFERIDO)) {
            LocalDateTime f = ch.getFechaPago();
            if (f == null || f.isAfter(hasta)) continue;
            Vencimiento v = new Vencimiento();
            v.setTipo("CHEQUE"); v.setReferenciaId(ch.getId());
            v.setDescripcion("Cheque " + (ch.getNumero() != null ? ch.getNumero().longValue() : ch.getId()));
            v.setMonto(BigDecimal.valueOf(ch.getTotal() != null ? ch.getTotal() : 0.0)); v.setFecha(f);
            v.setDiasRestantes(ChronoUnit.DAYS.between(ahora.toLocalDate(), f.toLocalDate()));
            out.add(v);
        }
        out.sort((a, b) -> Long.compare(a.getDiasRestantes(), b.getDiasRestantes()));
        return out;
    }

    @Data
    public static class Aging {
        private BigDecimal porVencer = BigDecimal.ZERO;
        private BigDecimal vencido = BigDecimal.ZERO;
    }

    /** Aging de CPP (por vencer vs vencido) sobre solicitudes pendientes/parciales. */
    public Aging agingCpp() {
        LocalDateTime ahora = LocalDateTime.now();
        Aging a = new Aging();
        for (SolicitudPago sp : solicitudPagoRepository.findByEstadoIn(SolicitudPagoEstado.DEUDA_ABIERTA)) {
            BigDecimal total = BigDecimal.valueOf(sp.getMontoTotal() != null ? sp.getMontoTotal() : 0.0);
            BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal saldo = total.subtract(pagado);
            LocalDateTime f = sp.getFechaPagoPropuesta();
            if (f != null && f.isBefore(ahora)) a.setVencido(a.getVencido().add(saldo));
            else a.setPorVencer(a.getPorVencer().add(saldo));
        }
        return a;
    }
}
