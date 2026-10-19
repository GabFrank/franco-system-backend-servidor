package com.franco.dev.service.rrhh.builder;

import com.franco.dev.service.rrhh.dto.AsistenciaDiaDto;
import com.franco.dev.service.rrhh.dto.AsistenciaResumenDto;
import com.franco.dev.service.rrhh.dto.EstadoAsistenciaDia;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculo puro de la puntuacion de asistencia del legajo.
 * puntaje = 10 x (1 - (pesoFalta x ausenciasInjustificadas + pesoTardanza x llegadasTardias) / diasEsperados).
 * Sin dependencias de Spring/JPA para poder testearlo de forma aislada.
 */
public final class AsistenciaCalculator {

    public static final BigDecimal PESO_FALTA_DEFAULT = new BigDecimal("3");
    public static final BigDecimal PESO_TARDANZA_DEFAULT = BigDecimal.ONE;

    private AsistenciaCalculator() {
    }

    /** Un dia en que al funcionario le correspondia trabajar, con lo que paso ese dia. */
    @Data
    @AllArgsConstructor
    public static class DiaLaborable {
        private LocalDate fecha;
        /** Hay jornada con marcacion de entrada. */
        private boolean tieneJornada;
        private long minutosTardiaEntrada;
        private long minutosTardiaAlmuerzo;
        /** Tiene un justificativo cuyo tipo evita la penalizacion. */
        private boolean justificado;
        private String justificativo;
    }

    /**
     * Tramo del mes que se evalua: del ingreso (o el dia 1) a hoy, al egreso o a fin de mes, lo
     * que ocurra primero. Null si el mes no tiene ningun dia evaluable.
     */
    public static LocalDate[] rango(int anho, int mes, LocalDate ingreso, LocalDate egreso, LocalDate hoy) {
        YearMonth ym = YearMonth.of(anho, mes);
        LocalDate desde = ym.atDay(1);
        LocalDate hasta = ym.atEndOfMonth();
        if (ingreso != null && ingreso.isAfter(desde)) desde = ingreso;
        if (egreso != null && egreso.isBefore(hasta)) hasta = egreso;
        if (hoy != null && hoy.isBefore(hasta)) hasta = hoy;
        return desde.isAfter(hasta) ? null : new LocalDate[]{desde, hasta};
    }

    /**
     * Franco semanal: se trabaja seis dias y uno es libre, sin dia fijo. En cada semana (lunes a
     * domingo) el primer dia sin jornada y sin justificar es el franco y deja de ser un dia
     * esperado; recien el segundo es una falta. Quien trabaja los siete dias no pierde nada.
     * La lista tiene que venir ordenada por fecha y arrancar en el lunes de la primera semana,
     * para no regalar un segundo franco a la semana que quedo partida entre dos meses.
     */
    public static List<DiaLaborable> sinFrancoSemanal(List<DiaLaborable> dias) {
        List<DiaLaborable> resultado = new ArrayList<>();
        LocalDate semanaConFranco = null;
        for (DiaLaborable d : dias) {
            LocalDate lunes = d.getFecha().with(DayOfWeek.MONDAY);
            if (!d.isTieneJornada() && !d.isJustificado() && !lunes.equals(semanaConFranco)) {
                semanaConFranco = lunes;
                continue;
            }
            resultado.add(d);
        }
        return resultado;
    }

    /**
     * La entrada y el regreso de almuerzo son eventos independientes: un mismo dia puede sumar
     * dos llegadas tardias. Un dia justificado no suma ninguna.
     */
    public static AsistenciaResumenDto calcular(int anho, int mes, boolean tieneHorario, List<DiaLaborable> dias,
                                                long toleranciaMin, BigDecimal pesoFalta, BigDecimal pesoTardanza) {
        BigDecimal pFalta = pesoFalta != null ? pesoFalta : PESO_FALTA_DEFAULT;
        BigDecimal pTardanza = pesoTardanza != null ? pesoTardanza : PESO_TARDANZA_DEFAULT;

        int trabajados = 0, aTiempo = 0, tardanzas = 0, justificadas = 0, injustificadas = 0;
        List<AsistenciaDiaDto> detalle = new ArrayList<>();

        for (DiaLaborable d : dias) {
            EstadoAsistenciaDia estado;
            if (d.isTieneJornada()) {
                trabajados++;
                int eventos = 0;
                if (!d.isJustificado()) {
                    if (d.getMinutosTardiaEntrada() > toleranciaMin) eventos++;
                    if (d.getMinutosTardiaAlmuerzo() > toleranciaMin) eventos++;
                }
                tardanzas += eventos;
                if (eventos == 0) aTiempo++;
                estado = eventos == 0 ? EstadoAsistenciaDia.TRABAJADO : EstadoAsistenciaDia.TARDANZA;
            } else if (d.isJustificado()) {
                justificadas++;
                estado = EstadoAsistenciaDia.AUSENCIA_JUSTIFICADA;
            } else {
                injustificadas++;
                estado = EstadoAsistenciaDia.AUSENCIA_INJUSTIFICADA;
            }
            detalle.add(new AsistenciaDiaDto(d.getFecha().toString(), estado,
                    (int) d.getMinutosTardiaEntrada(), (int) d.getMinutosTardiaAlmuerzo(), d.getJustificativo()));
        }

        int esperados = dias.size();
        Integer puntualidad = trabajados == 0 ? null
                : new BigDecimal(aTiempo * 100).divide(new BigDecimal(trabajados), 0, RoundingMode.HALF_UP).intValue();

        Double puntaje = null;
        if (esperados > 0) {
            BigDecimal descuento = pFalta.multiply(new BigDecimal(injustificadas))
                    .add(pTardanza.multiply(new BigDecimal(tardanzas)));
            BigDecimal valor = BigDecimal.TEN.multiply(
                    BigDecimal.ONE.subtract(descuento.divide(new BigDecimal(esperados), 6, RoundingMode.HALF_UP)));
            valor = valor.max(BigDecimal.ZERO).min(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP);
            puntaje = valor.doubleValue();
        }

        return new AsistenciaResumenDto(anho, mes, tieneHorario, esperados, trabajados, tardanzas,
                justificadas, injustificadas, puntualidad, puntaje, detalle);
    }
}
