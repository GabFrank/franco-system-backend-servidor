package com.franco.dev.service.rrhh.builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Base remunerativa de un funcionario en un anio: lo que efectivamente percibio,
 * mes por mes, contando solo los conceptos que retribuyen trabajo.
 *
 * <p>Es el punto unico del que salen el aguinaldo (anual y proporcional), la base del
 * IPS del finiquito y la base de la indemnizacion. Antes cada uno tenia su propia
 * formula: el aguinaldo anual usaba el sueldo actual y puntual, y el finiquito sumaba
 * total_haberes -- que incluye el propio aguinaldo. El mismo funcionario cobraba
 * distinto segun si se quedaba o si se iba.</p>
 *
 * <p>Calculo puro, sin Spring ni JPA: recibe los montos ya agregados por la query y
 * decide. Lo que entra a la suma lo define {@code es_remunerativo} en el catalogo de
 * conceptos, no esta clase.</p>
 */
public final class BaseRemunerativa {

    private BaseRemunerativa() {
    }

    /** Lo percibido en un mes del anio. {@code mes} es 1..12. */
    public static final class Mes {
        public final int mes;
        public final BigDecimal monto;

        public Mes(int mes, BigDecimal monto) {
            this.mes = mes;
            this.monto = monto != null ? monto : BigDecimal.ZERO;
        }
    }

    public static final class Resultado {
        /** Suma de lo percibido en el anio. */
        public final BigDecimal total;
        /** Cuantos meses del anio tienen liquidacion contada. */
        public final int mesesConLiquidacion;
        /** Primer y ultimo mes con liquidacion (0 si no hay ninguna). */
        public final int primerMes;
        public final int ultimoMes;
        /**
         * Si falta algun mes ENTRE el primero y el ultimo.
         *
         * <p>Que falte el mes en curso no es un hueco: es el ciclo normal de nomina, y
         * avisar por eso seria ruido todos los meses. Un hueco en el medio si significa
         * que hay una liquidacion sin cargar, y ahi el aguinaldo sale bajo sin que se
         * note.</p>
         */
        public final boolean hayHueco;

        Resultado(BigDecimal total, int mesesConLiquidacion, int primerMes, int ultimoMes, boolean hayHueco) {
            this.total = total;
            this.mesesConLiquidacion = mesesConLiquidacion;
            this.primerMes = primerMes;
            this.ultimoMes = ultimoMes;
            this.hayHueco = hayHueco;
        }

        public boolean vacio() {
            return mesesConLiquidacion == 0;
        }

        /**
         * Promedio mensual observado. Es lo que se proyecta sobre los meses que faltan
         * para llegar al 31/12: el proyectado no puede salir del percibido directo
         * porque no existen liquidaciones de meses futuros.
         */
        public BigDecimal promedioMensual() {
            if (mesesConLiquidacion <= 0) return BigDecimal.ZERO;
            return total.divide(new BigDecimal(mesesConLiquidacion), 0, RoundingMode.HALF_UP);
        }
    }

    /** Aguinaldo = percibido / 12, en guaranies enteros. */
    public static BigDecimal aguinaldo(BigDecimal percibido) {
        BigDecimal v = percibido != null ? percibido : BigDecimal.ZERO;
        return v.divide(new BigDecimal("12"), 0, RoundingMode.HALF_UP);
    }

    /**
     * Aguinaldo proyectado al 31/12: se extiende el promedio mensual observado sobre los
     * meses proyectados. Con el anio ya terminado coincide con {@link #aguinaldo}.
     */
    public static BigDecimal aguinaldoProyectado(Resultado r, int mesesProyectados) {
        if (r == null || r.vacio() || mesesProyectados <= 0) return BigDecimal.ZERO;
        return r.promedioMensual()
                .multiply(new BigDecimal(mesesProyectados))
                .divide(new BigDecimal("12"), 0, RoundingMode.HALF_UP);
    }

    public static Resultado de(List<Mes> meses) {
        if (meses == null || meses.isEmpty()) {
            return new Resultado(BigDecimal.ZERO, 0, 0, 0, false);
        }
        List<Integer> presentes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int primero = Integer.MAX_VALUE;
        int ultimo = 0;
        for (Mes m : meses) {
            if (m == null || m.mes < 1 || m.mes > 12) continue;
            total = total.add(m.monto);
            presentes.add(m.mes);
            if (m.mes < primero) primero = m.mes;
            if (m.mes > ultimo) ultimo = m.mes;
        }
        if (presentes.isEmpty()) {
            return new Resultado(BigDecimal.ZERO, 0, 0, 0, false);
        }
        // Hueco = el tramo entre el primero y el ultimo tiene mas meses que los contados.
        boolean hueco = (ultimo - primero + 1) > presentes.size();
        return new Resultado(total, presentes.size(), primero, ultimo, hueco);
    }

    /** Extrae el mes de un periodo "YYYY-MM". Devuelve 0 si no tiene esa forma. */
    public static int mesDePeriodo(String periodo) {
        if (periodo == null || periodo.length() < 7 || periodo.charAt(4) != '-') return 0;
        try {
            int m = Integer.parseInt(periodo.substring(5, 7));
            return (m >= 1 && m <= 12) ? m : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
