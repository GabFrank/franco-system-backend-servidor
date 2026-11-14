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
    private final ChequeGestionService chequeGestionService;

    /** Tope del tablero: los más nuevos primero. Un rango largo con «Todos» no trae la tabla entera. */
    static final int TOPE_TABLERO = 500;

    /** Qué fecha filtra el tablero (issue #601). Sin indicar, PAGO: lo de siempre para un desktop anterior. */
    public enum TipoFecha { EMISION, PAGO, COBRO;
        public static TipoFecha de(String s) {
            if (s == null || s.trim().isEmpty()) return PAGO;
            try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) {
                throw new graphql.GraphQLException("Tipo de fecha inválido: " + s + " (EMISION, PAGO o COBRO)");
            }
        }
    }

    /** Filtros del tablero. */
    @Data
    public static class Filtro {
        private LocalDateTime desde;
        private LocalDateTime hasta;
        private Long cuentaId;
        private Long chequeraId;
        private TipoFecha tipoFecha = TipoFecha.PAGO;
        /** Vacío = todos los estados. */
        private java.util.List<EstadoCheque> estados = new ArrayList<>();
        private String texto;
        private Long proveedorId;
    }

    /** Cheques por fecha de pago en el rango, con filtros opcionales (firma anterior, la usa saldosPorChequera). */
    public List<Cheque> filtrar(LocalDateTime desde, LocalDateTime hasta, Long cuentaId, Long chequeraId, EstadoCheque estado) {
        return chequeRepository.filtrarPorFechaPago(desde, hasta, cuentaId, chequeraId, estado);
    }

    public List<Cheque> filtrar(Filtro f) {
        boolean todos = f.getEstados() == null || f.getEstados().isEmpty();
        // Una lista vacía en un IN no es SQL válido: con «todos» el parámetro no se mira, pero tiene que tener algo.
        java.util.List<EstadoCheque> estados = todos ? java.util.Collections.singletonList(EstadoCheque.DIFERIDO) : f.getEstados();
        String texto = f.getTexto() != null && !f.getTexto().trim().isEmpty() ? "%" + f.getTexto().trim().toUpperCase() + "%" : null;
        return chequeRepository.filtrarTablero(f.getTipoFecha().name(), f.getDesde(), f.getHasta(), f.getCuentaId(),
                f.getChequeraId(), todos, estados, f.getProveedorId(), texto,
                org.springframework.data.domain.PageRequest.of(0, TOPE_TABLERO));
    }

    /** La fecha por la que se agrupa el resumen: la misma que filtra. */
    private static LocalDateTime fechaDe(Cheque c, TipoFecha tipo) {
        switch (tipo) {
            case EMISION: return c.getFechaEntrega() != null ? c.getFechaEntrega() : c.getCreadoEn();
            case COBRO: return c.getFechaCobro();
            default: return c.getFechaPago();
        }
    }

    /** Total y cantidad de cheques a pagar por día (para el gráfico y el KPI por fecha). */
    public List<ResumenDia> resumenPorDia(LocalDateTime desde, LocalDateTime hasta, Long cuentaId, Long chequeraId, EstadoCheque estado) {
        Filtro f = new Filtro();
        f.setDesde(desde); f.setHasta(hasta); f.setCuentaId(cuentaId); f.setChequeraId(chequeraId);
        if (estado != null) f.getEstados().add(estado);
        return resumenPorDia(f);
    }

    public List<ResumenDia> resumenPorDia(Filtro filtro) {
        Map<LocalDate, ResumenDia> map = new TreeMap<>();
        for (Cheque c : filtrar(filtro)) {
            LocalDateTime fecha = fechaDe(c, filtro.getTipoFecha());
            if (fecha == null) continue;
            LocalDate d = fecha.toLocalDate();
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
        LocalDateTime desde = LocalDateTime.of(1970, 1, 1, 0, 0);
        Map<Long, Double> pendientePorChequera = new HashMap<>();
        // Sin estado, lo pendiente es lo que todavía reserva: los diferidos y los al día emitidos (issue #601).
        List<Cheque> pendientes = new ArrayList<>(chequeRepository.filtrarPorFechaPago(desde, hasta, null, null,
                estado != null ? estado : EstadoCheque.DIFERIDO));
        if (estado == null) pendientes.addAll(chequeRepository.filtrarPorFechaPago(desde, hasta, null, null, EstadoCheque.EMITIDO));
        for (Cheque c : pendientes) {
            if (c.getChequera() == null) continue;
            pendientePorChequera.merge(c.getChequera().getId(), c.getTotal() != null ? c.getTotal() : 0.0, Double::sum);
        }
        List<SaldoChequera> out = new ArrayList<>();
        // Las activas, y las que ya no lo son pero tienen cheques pendientes: una chequera agotada con un diferido sin
        // cobrar sacaba ese monto de lo pendiente y de la posición general.
        List<Chequera> chequeras = new ArrayList<>(chequeraRepository.findByEstadoOrderByIdDesc(EstadoChequera.ACTIVA));
        java.util.Set<Long> incluidas = new java.util.HashSet<>();
        chequeras.forEach(ch -> incluidas.add(ch.getId()));
        java.util.Map<Long, Chequera> otras = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        // Para entrar alcanza un pendiente con cualquier fecha de pago: su reserva sigue en la cuenta aunque el pago
        // caiga después de «hasta».
        LocalDateTime sinTope = LocalDateTime.of(9999, 12, 31, 23, 59);
        List<Cheque> todosPendientes = new ArrayList<>(chequeRepository.filtrarPorFechaPago(desde, sinTope, null, null,
                estado != null ? estado : EstadoCheque.DIFERIDO));
        if (estado == null) todosPendientes.addAll(chequeRepository.filtrarPorFechaPago(desde, sinTope, null, null, EstadoCheque.EMITIDO));
        for (Cheque c : todosPendientes) {
            Chequera ch = c.getChequera();
            if (ch != null && !incluidas.contains(ch.getId())) otras.putIfAbsent(ch.getId(), ch);
        }
        chequeras.addAll(otras.values());
        for (Chequera ch : chequeras) {
            SaldoChequera s = new SaldoChequera();
            s.setChequera(ch);
            s.setCuentaBancaria(ch.getCuentaBancaria());
            s.setPendienteHastaFecha(pendientePorChequera.getOrDefault(ch.getId(), 0.0));
            if (ch.getCuentaBancaria() != null) {
                s.setSaldoCuenta(ch.getCuentaBancaria().getSaldo() != null ? ch.getCuentaBancaria().getSaldo().doubleValue() : 0.0);
                s.setSaldoReservado(ch.getCuentaBancaria().getSaldoReservado() != null ? ch.getCuentaBancaria().getSaldoReservado().doubleValue() : 0.0);
            }
            // Sin los inutilizados (issue #603): la misma cuenta que la emisión.
            s.setHojasDisponibles(ch.getRangoHasta() == null ? 0
                    : (int) Math.min(Integer.MAX_VALUE, chequeGestionService.hojasLibres(ch)));
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
