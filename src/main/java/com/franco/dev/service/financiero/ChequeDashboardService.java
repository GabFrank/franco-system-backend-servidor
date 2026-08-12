package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.ChequeraRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Datos del dashboard de cheques, todo ordenado/agregado por FECHA DE PAGO (vencimiento):
 * lista filtrada, total por día (para el gráfico/KPI) y saldos por chequera (para los cards).
 */
@Service
@AllArgsConstructor
public class ChequeDashboardService {

    private final ChequeRepository chequeRepository;
    private final ChequeraRepository chequeraRepository;

    /** Cheques por fecha de pago en el rango, con filtros opcionales. */
    public List<Cheque> filtrar(LocalDateTime desde, LocalDateTime hasta, Long cuentaId, Long chequeraId, EstadoCheque estado) {
        return chequeRepository.filtrarPorFechaPago(desde, hasta, cuentaId, chequeraId, estado);
    }

    /** Total y cantidad de cheques a pagar por día (para el gráfico y el KPI por fecha). */
    public List<ResumenDia> resumenPorDia(LocalDateTime desde, LocalDateTime hasta, Long cuentaId, Long chequeraId, EstadoCheque estado) {
        Map<LocalDate, ResumenDia> map = new TreeMap<>();
        for (Cheque c : filtrar(desde, hasta, cuentaId, chequeraId, estado)) {
            if (c.getFechaPago() == null) continue;
            LocalDate d = c.getFechaPago().toLocalDate();
            ResumenDia r = map.computeIfAbsent(d, k -> {
                ResumenDia x = new ResumenDia();
                x.setFecha(k.toString());
                x.setTotal(0.0);
                x.setCantidad(0);
                return x;
            });
            r.setTotal(r.getTotal() + (c.getTotal() != null ? c.getTotal() : 0.0));
            r.setCantidad(r.getCantidad() + 1);
        }
        return new ArrayList<>(map.values());
    }

    /** Un item por chequera activa: pendiente hasta la fecha + saldo/reservado de la cuenta + hojas. */
    public List<SaldoChequera> saldosPorChequera(LocalDateTime hasta, EstadoCheque estado) {
        EstadoCheque est = estado != null ? estado : EstadoCheque.DIFERIDO;
        LocalDateTime desde = LocalDateTime.of(1970, 1, 1, 0, 0);
        Map<Long, Double> pendientePorChequera = new HashMap<>();
        for (Cheque c : chequeRepository.filtrarPorFechaPago(desde, hasta, null, null, est)) {
            if (c.getChequera() == null) continue;
            pendientePorChequera.merge(c.getChequera().getId(), c.getTotal() != null ? c.getTotal() : 0.0, Double::sum);
        }
        List<SaldoChequera> out = new ArrayList<>();
        for (Chequera ch : chequeraRepository.findByEstadoOrderByIdDesc(EstadoChequera.ACTIVA)) {
            SaldoChequera s = new SaldoChequera();
            s.setChequera(ch);
            s.setCuentaBancaria(ch.getCuentaBancaria());
            s.setPendienteHastaFecha(pendientePorChequera.getOrDefault(ch.getId(), 0.0));
            if (ch.getCuentaBancaria() != null) {
                s.setSaldoCuenta(ch.getCuentaBancaria().getSaldo() != null ? ch.getCuentaBancaria().getSaldo().doubleValue() : 0.0);
                s.setSaldoReservado(ch.getCuentaBancaria().getSaldoReservado() != null ? ch.getCuentaBancaria().getSaldoReservado().doubleValue() : 0.0);
            }
            double hojas = 0;
            if (ch.getRangoHasta() != null) {
                double sig = ch.getSiguienteNumero() != null ? ch.getSiguienteNumero()
                        : (ch.getRangoDesde() != null ? ch.getRangoDesde() : 0);
                hojas = Math.max(0, Math.floor(ch.getRangoHasta() - sig + 1));
            }
            s.setHojasDisponibles((int) hojas);
            out.add(s);
        }
        return out;
    }

    @Data
    public static class ResumenDia {
        private String fecha;      // yyyy-MM-dd
        private Double total;
        private Integer cantidad;
    }

    @Data
    public static class SaldoChequera {
        private Chequera chequera;
        private CuentaBancaria cuentaBancaria;
        private Double pendienteHastaFecha;
        private Double saldoCuenta;
        private Double saldoReservado;
        private Integer hojasDisponibles;
    }
}
