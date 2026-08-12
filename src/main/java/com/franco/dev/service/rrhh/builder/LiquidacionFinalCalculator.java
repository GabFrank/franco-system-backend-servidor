package com.franco.dev.service.rrhh.builder;

import com.franco.dev.domain.rrhh.enums.MotivoEgreso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cálculo puro del finiquito (liquidación final) — espejo de Gourmet §4.8.
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 *
 *   1. antigüedad = diff(ingreso, egreso) → días, meses (÷30), años (÷365)
 *   2. indemnización — solo DESPIDO_INJUSTIFICADO y antigüedad ≥ 90 días:
 *        salarioPromedio/30 × díasPorAño × max(1, años)
 *   3. vacaciones no gozadas = díasNoGozados × salarioPromedio/30
 *   4. aguinaldo proporcional = viene calculado (Σ haberes del año / 12)
 *   5. total = indemnización + vacaciones + aguinaldo
 */
public final class LiquidacionFinalCalculator {

    private static final BigDecimal TREINTA = new BigDecimal("30");

    private LiquidacionFinalCalculator() {
    }

    public static class Antiguedad {
        private final long dias;
        private final int meses;
        private final int anios;

        public Antiguedad(long dias, int meses, int anios) {
            this.dias = dias;
            this.meses = meses;
            this.anios = anios;
        }

        public long getDias() { return dias; }
        public int getMeses() { return meses; }
        public int getAnios() { return anios; }
    }

    public static class Resultado {
        private final Antiguedad antiguedad;
        private final boolean indemnizacionAplica;
        private final BigDecimal indemnizacionMonto;
        private final int diasNoGozados;
        private final BigDecimal montoVacacionesNoGozadas;
        private final BigDecimal aguinaldoProporcional;
        // Preaviso: aplica según motivo + si se otorgó. HABER en despido injustificado
        // sin preaviso; DESCUENTO (½ del tramo) en renuncia sin preaviso.
        private final boolean preavisoAplica;
        private final int preavisoDias;
        private final BigDecimal preavisoMonto;
        private final boolean preavisoEsDescuento;
        private final BigDecimal totalLiquidado;

        public Resultado(Antiguedad antiguedad, boolean indemnizacionAplica, BigDecimal indemnizacionMonto,
                         int diasNoGozados, BigDecimal montoVacacionesNoGozadas,
                         BigDecimal aguinaldoProporcional, boolean preavisoAplica, int preavisoDias,
                         BigDecimal preavisoMonto, boolean preavisoEsDescuento, BigDecimal totalLiquidado) {
            this.antiguedad = antiguedad;
            this.indemnizacionAplica = indemnizacionAplica;
            this.indemnizacionMonto = indemnizacionMonto;
            this.diasNoGozados = diasNoGozados;
            this.montoVacacionesNoGozadas = montoVacacionesNoGozadas;
            this.aguinaldoProporcional = aguinaldoProporcional;
            this.preavisoAplica = preavisoAplica;
            this.preavisoDias = preavisoDias;
            this.preavisoMonto = preavisoMonto;
            this.preavisoEsDescuento = preavisoEsDescuento;
            this.totalLiquidado = totalLiquidado;
        }

        public Antiguedad getAntiguedad() { return antiguedad; }
        public boolean isIndemnizacionAplica() { return indemnizacionAplica; }
        public BigDecimal getIndemnizacionMonto() { return indemnizacionMonto; }
        public int getDiasNoGozados() { return diasNoGozados; }
        public BigDecimal getMontoVacacionesNoGozadas() { return montoVacacionesNoGozadas; }
        public BigDecimal getAguinaldoProporcional() { return aguinaldoProporcional; }
        public boolean isPreavisoAplica() { return preavisoAplica; }
        public int getPreavisoDias() { return preavisoDias; }
        public BigDecimal getPreavisoMonto() { return preavisoMonto; }
        public boolean isPreavisoEsDescuento() { return preavisoEsDescuento; }
        public BigDecimal getTotalLiquidado() { return totalLiquidado; }
    }

    /** Antigüedad entre ingreso y egreso. Si egreso &lt; ingreso, todo cero. */
    /** Antigüedad con divisores por defecto (30 días/mes, 365 días/año). */
    public static Antiguedad antiguedad(LocalDate ingreso, LocalDate egreso) {
        return antiguedad(ingreso, egreso, 30, 365);
    }

    public static Antiguedad antiguedad(LocalDate ingreso, LocalDate egreso, int diasMes, int diasAnio) {
        if (ingreso == null || egreso == null || egreso.isBefore(ingreso)) {
            return new Antiguedad(0, 0, 0);
        }
        int dm = diasMes > 0 ? diasMes : 30;
        int da = diasAnio > 0 ? diasAnio : 365;
        long dias = ChronoUnit.DAYS.between(ingreso, egreso);
        int meses = (int) (dias / dm);
        int anios = (int) (dias / da);
        return new Antiguedad(dias, meses, anios);
    }

    public static Resultado calcular(LocalDate ingreso, LocalDate egreso, MotivoEgreso motivo,
                                     BigDecimal salarioPromedio, int diasNoGozados,
                                     BigDecimal aguinaldoProporcional, BigDecimal indemnizacionDiasPorAnio,
                                     int minDiasIndemnizacion, int diasMes, int diasAnio,
                                     int preavisoDias, boolean preavisoOtorgado) {
        BigDecimal promedio = salarioPromedio != null ? salarioPromedio : BigDecimal.ZERO;
        BigDecimal diasPorAnio = indemnizacionDiasPorAnio != null ? indemnizacionDiasPorAnio : BigDecimal.ZERO;
        BigDecimal aguinaldo = aguinaldoProporcional != null ? aguinaldoProporcional : BigDecimal.ZERO;
        int diasVac = Math.max(0, diasNoGozados);
        BigDecimal divisorDiasMes = new BigDecimal(diasMes > 0 ? diasMes : 30);

        Antiguedad ant = antiguedad(ingreso, egreso, diasMes, diasAnio);

        boolean indemnizaAplica = motivo == MotivoEgreso.DESPIDO_INJUSTIFICADO
                && ant.getDias() >= minDiasIndemnizacion;
        BigDecimal indemnizacion = BigDecimal.ZERO;
        if (indemnizaAplica) {
            int anios = Math.max(1, ant.getAnios());
            // salarioPromedio/diasMes × díasPorAño × años. Guaraníes: sin decimales (scale 0).
            indemnizacion = promedio.multiply(diasPorAnio).multiply(new BigDecimal(anios))
                    .divide(divisorDiasMes, 0, RoundingMode.HALF_UP);
        }

        BigDecimal montoVac = promedio.multiply(new BigDecimal(diasVac))
                .divide(divisorDiasMes, 0, RoundingMode.HALF_UP);

        // Preaviso: solo si NO se otorgó. Despido injustificado → el empleador lo paga
        // (HABER, días completos del tramo); renuncia → el empleador lo descuenta
        // (DESCUENTO, la mitad de los días del tramo).
        boolean preavisoAplica = false;
        boolean preavisoEsDescuento = false;
        BigDecimal preavisoMonto = BigDecimal.ZERO;
        int preDias = Math.max(0, preavisoDias);
        if (!preavisoOtorgado && preDias > 0) {
            BigDecimal diario = promedio.divide(divisorDiasMes, 4, RoundingMode.HALF_UP);
            if (motivo == MotivoEgreso.DESPIDO_INJUSTIFICADO) {
                preavisoAplica = true;
                preavisoEsDescuento = false;
                preavisoMonto = diario.multiply(new BigDecimal(preDias)).setScale(0, RoundingMode.HALF_UP);
            } else if (motivo == MotivoEgreso.RENUNCIA) {
                preavisoAplica = true;
                preavisoEsDescuento = true;
                // mitad del tramo
                BigDecimal mitadDias = new BigDecimal(preDias).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
                preavisoMonto = diario.multiply(mitadDias).setScale(0, RoundingMode.HALF_UP);
            }
        }

        BigDecimal total = indemnizacion.add(montoVac).add(aguinaldo);
        if (preavisoAplica) {
            total = preavisoEsDescuento ? total.subtract(preavisoMonto) : total.add(preavisoMonto);
        }

        return new Resultado(ant, indemnizaAplica, indemnizacion, diasVac, montoVac, aguinaldo,
                preavisoAplica, preavisoAplica ? preDias : 0, preavisoMonto, preavisoEsDescuento, total);
    }
}
