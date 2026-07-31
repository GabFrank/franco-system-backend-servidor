package com.franco.dev.service.financiero;

import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
}
