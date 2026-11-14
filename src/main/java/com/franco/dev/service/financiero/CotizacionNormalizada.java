package com.franco.dev.service.financiero;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Cotización de un cambio de divisa en formato de casa de cambio (issue #603): <b>unidades de la moneda de menor
 * valor por 1 de la de mayor valor</b> (1 US$ = 5,60 R$; 1 US$ = 6.400 Gs). Antes el mismo campo guardaba a veces
 * «destino por 1 origen» y a veces lo inverso (en bodega, REAL→DOLAR: 0,1825 y 5,60).
 *
 * <p>La moneda mayor sale de los montos, que son los reales (la diferencia del cambio es un ajuste aparte): la de
 * monto menor vale más. Así no hace falta ninguna cotización del sistema para decidirlo.
 *
 * <p>Regla, la misma que aplica V276.5 a lo ya guardado: si la cotización recibida cierra con los montos (±1%) se
 * conserva; si cierra invertida, se invierte; si no cierra de ninguna forma (o no vino), se usa la de los montos.
 */
public final class CotizacionNormalizada {

    /** Tolerancia relativa: atrapa una inversión (decenas de veces) sin rechazar el redondeo de los montos. */
    static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    /** true si la moneda de origen es la de mayor valor (monto menor). Empate: el origen. */
    public final boolean mayorEsOrigen;
    /** Cotización normalizada, «menor por 1 mayor», con 6 decimales (como la columna). */
    public final BigDecimal cotizacion;
    /** La recibida cerraba con los montos tal cual (sin invertir ni recalcular). */
    public final boolean recibidaCoincide;

    private CotizacionNormalizada(boolean mayorEsOrigen, BigDecimal cotizacion, boolean recibidaCoincide) {
        this.mayorEsOrigen = mayorEsOrigen;
        this.cotizacion = cotizacion;
        this.recibidaCoincide = recibidaCoincide;
    }

    /** null si no hay dos montos positivos con los que decidir. */
    public static CotizacionNormalizada de(BigDecimal montoOrigen, BigDecimal montoDestino, BigDecimal recibida) {
        if (montoOrigen == null || montoDestino == null || montoOrigen.signum() <= 0 || montoDestino.signum() <= 0) {
            return null;
        }
        boolean mayorEsOrigen = montoOrigen.compareTo(montoDestino) <= 0;
        BigDecimal montoMayor = mayorEsOrigen ? montoOrigen : montoDestino;
        BigDecimal montoMenor = mayorEsOrigen ? montoDestino : montoOrigen;
        BigDecimal deLosMontos = montoMenor.divide(montoMayor, MC);

        if (recibida != null && recibida.signum() > 0) {
            if (cierra(recibida, deLosMontos)) return new CotizacionNormalizada(mayorEsOrigen, escala(recibida), true);
            BigDecimal invertida = BigDecimal.ONE.divide(recibida, MC);
            if (cierra(invertida, deLosMontos)) return new CotizacionNormalizada(mayorEsOrigen, escala(invertida), false);
        }
        return new CotizacionNormalizada(mayorEsOrigen, escala(deLosMontos), false);
    }

    static boolean cierra(BigDecimal cot, BigDecimal deLosMontos) {
        return cot.subtract(deLosMontos).abs().compareTo(deLosMontos.multiply(TOLERANCIA)) <= 0;
    }

    private static BigDecimal escala(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_UP);
    }
}
