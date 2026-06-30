package com.franco.dev.service.financiero;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.domain.financiero.EnteFinanciero;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.SituacionPagoEnte;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import com.franco.dev.repository.financiero.EnteCuotaRepository;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActivoFinancieroSyncService {

    private final EnteService enteService;
    private final EnteFinancieroService enteFinancieroService;
    private final EnteCuotaRepository enteCuotaRepository;
    private final UsuarioService usuarioService;

    @Transactional
    public void syncFromAsset(
            TipoEnte tipoEnte,
            Long referenciaId,
            String situacionPago,
            Long proveedorId,
            Long monedaId,
            BigDecimal montoTotal,
            BigDecimal montoYaPagado,
            Integer cantidadCuotas,
            Integer cantidadCuotasPagadas,
            Integer diaVencimiento,
            List<CuotaDetalleInput> cuotasDetalle,
            Long usuarioId
    ) {
        if (referenciaId == null || situacionPago == null) {
            return;
        }

        Optional<Ente> enteOpt = enteService.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId);
        if (enteOpt.isEmpty()) {
            return;
        }

        Ente ente = enteOpt.get();
        SituacionPagoEnte situacion = parseSituacion(situacionPago);
        if (situacion == null) {
            return;
        }

        EnteFinanciero financiero = enteFinancieroService.findByEnteId(ente.getId()).orElse(new EnteFinanciero());
        financiero.setEnte(ente);
        financiero.setSituacionPago(situacion);

        if (proveedorId != null) {
            Persona proveedor = new Persona();
            proveedor.setId(proveedorId);
            financiero.setProveedor(proveedor);
        }
        if (monedaId != null) {
            Moneda moneda = new Moneda();
            moneda.setId(monedaId);
            financiero.setMoneda(moneda);
        }
        if (usuarioId != null) {
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            financiero.setUsuario(usuario);
        }

        financiero.setMontoTotal(montoTotal);
        financiero.setMontoYaPagado(montoYaPagado);
        financiero.setCantidadCuotas(cantidadCuotas);
        financiero.setDiaVencimiento(diaVencimiento);
        financiero = enteFinancieroService.save(financiero);

        if (!SituacionPagoEnte.PAGANDO.equals(situacion)) {
            enteCuotaRepository.deleteByEnteFinancieroId(financiero.getId());
            return;
        }

        List<EnteCuota> cuotas = buildCuotas(
                financiero,
                cantidadCuotas,
                cantidadCuotasPagadas,
                diaVencimiento,
                montoTotal,
                montoYaPagado,
                cuotasDetalle,
                usuarioId
        );

        enteCuotaRepository.deleteByEnteFinancieroId(financiero.getId());
        for (EnteCuota cuota : cuotas) {
            enteCuotaRepository.save(cuota);
        }

        BigDecimal totalCuotasPendientes = cuotas.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getPagado()))
                .map(c -> c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCuotasPendientes.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal yaPagado = montoYaPagado != null ? montoYaPagado : BigDecimal.ZERO;
            BigDecimal totalDesdeCuotas = yaPagado.add(totalCuotasPendientes);
            BigDecimal totalActual = financiero.getMontoTotal() != null ? financiero.getMontoTotal() : BigDecimal.ZERO;
            if (totalDesdeCuotas.compareTo(totalActual) > 0) {
                financiero.setMontoTotal(totalDesdeCuotas);
                enteFinancieroService.save(financiero);
            }
        }
    }

    private List<EnteCuota> buildCuotas(
            EnteFinanciero financiero,
            Integer cantidadCuotas,
            Integer cantidadCuotasPagadas,
            Integer diaVencimiento,
            BigDecimal montoTotal,
            BigDecimal montoYaPagado,
            List<CuotaDetalleInput> cuotasDetalle,
            Long usuarioId
    ) {
        int total = cantidadCuotas != null && cantidadCuotas > 0 ? cantidadCuotas : 0;
        int pagadas = cantidadCuotasPagadas != null ? Math.max(0, cantidadCuotasPagadas) : 0;
        int dia = diaVencimiento != null && diaVencimiento >= 1 && diaVencimiento <= 31 ? diaVencimiento : 1;

        if (total <= 0) {
            return List.of();
        }

        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        List<EnteCuota> result = new ArrayList<>();

        if (cuotasDetalle != null && !cuotasDetalle.isEmpty()) {
            for (CuotaDetalleInput detalle : cuotasDetalle) {
                if (detalle.getNumeroCuota() == null) {
                    continue;
                }
                EnteCuota cuota = new EnteCuota();
                cuota.setEnteFinanciero(financiero);
                cuota.setNumeroCuota(detalle.getNumeroCuota());
                cuota.setMonto(detalle.getMonto());
                cuota.setPagado(detalle.getPagado() != null
                        ? detalle.getPagado()
                        : detalle.getNumeroCuota() <= pagadas);
                cuota.setFechaVencimiento(calcularFechaVencimiento(detalle.getNumeroCuota(), dia));
                cuota.setUsuario(usuario);
                result.add(cuota);
            }
            return result;
        }

        BigDecimal totalMonto = montoTotal != null ? montoTotal : BigDecimal.ZERO;
        BigDecimal yaPagado = montoYaPagado != null ? montoYaPagado : BigDecimal.ZERO;
        BigDecimal pendiente = totalMonto.subtract(yaPagado).max(BigDecimal.ZERO);
        int cuotasPendientes = Math.max(0, total - pagadas);
        BigDecimal montoPorCuota = cuotasPendientes > 0
                ? pendiente.divide(BigDecimal.valueOf(cuotasPendientes), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        for (int i = 1; i <= total; i++) {
            EnteCuota cuota = new EnteCuota();
            cuota.setEnteFinanciero(financiero);
            cuota.setNumeroCuota(i);
            cuota.setPagado(i <= pagadas);
            cuota.setMonto(i <= pagadas ? BigDecimal.ZERO : montoPorCuota);
            cuota.setFechaVencimiento(calcularFechaVencimiento(i, dia));
            cuota.setUsuario(usuario);
            result.add(cuota);
        }
        return result;
    }

    private LocalDate calcularFechaVencimiento(int numeroCuota, int diaVencimiento) {
        LocalDate base = LocalDate.now();
        return base.plusMonths(numeroCuota - 1L).withDayOfMonth(Math.min(diaVencimiento, base.plusMonths(numeroCuota - 1L).lengthOfMonth()));
    }

    private SituacionPagoEnte parseSituacion(String situacionPago) {
        try {
            return SituacionPagoEnte.valueOf(situacionPago.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
