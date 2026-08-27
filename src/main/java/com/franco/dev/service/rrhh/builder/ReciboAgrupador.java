package com.franco.dev.service.rrhh.builder;

import com.franco.dev.domain.rrhh.LiquidacionItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Politicas de agrupacion de items para la IMPRESION del recibo de liquidacion.
 *
 * <p>Clase pura, sin Spring ni JPA, para poder testearla sola — mismo criterio que
 * LiquidacionCalculator y los demas builders del modulo.</p>
 *
 * <p><b>Nada de esto se persiste.</b> Los LiquidacionItem individuales tienen que seguir
 * existiendo en la base porque {@code aplicarEfectosCruzados} usa el {@code referenciaId}
 * de cada uno para saldar su cuota al pagar la liquidacion. Consolidar en el modelo
 * descontaria la plata del sueldo y dejaria las cuotas vivas.</p>
 */
public final class ReciboAgrupador {

    public static final String CODIGO_CREDITO_CONVENIO = "CREDITO_CONVENIO_CUOTA";

    private ReciboAgrupador() { }

    /**
     * Junta las cuotas de compras a credito en una sola linea.
     *
     * <p>Solo alcanza a {@code CREDITO_CONVENIO_CUOTA}: las cuotas de prestamo son
     * {@code CPP_CUOTA} y quedan desglosadas, que es lo que se pidio.</p>
     *
     * <p>Con menos de dos cuotas devuelve la lista intacta: agrupar una sola perderia el
     * detalle (que venta la origino) a cambio de nada.</p>
     */
    public static List<LiquidacionItem> consolidarCuotasCredito(List<LiquidacionItem> items) {
        if (items == null || items.isEmpty()) return items;

        BigDecimal total = BigDecimal.ZERO;
        int cuantas = 0;
        LiquidacionItem primera = null;
        for (LiquidacionItem it : items) {
            if (CODIGO_CREDITO_CONVENIO.equals(it.getCodigo())) {
                total = total.add(it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO);
                cuantas++;
                if (primera == null) primera = it;
            }
        }
        if (cuantas < 2) return items;

        List<LiquidacionItem> salida = new ArrayList<>();
        boolean puesta = false;
        for (LiquidacionItem it : items) {
            if (!CODIGO_CREDITO_CONVENIO.equals(it.getCodigo())) {
                salida.add(it);
                continue;
            }
            if (puesta) continue;
            LiquidacionItem agrupado = new LiquidacionItem();
            agrupado.setLiquidacion(primera.getLiquidacion());
            agrupado.setCodigo(CODIGO_CREDITO_CONVENIO);
            agrupado.setDescripcion("COMPRAS A CREDITO (" + cuantas + " CUOTAS)");
            agrupado.setMonto(total);
            agrupado.setTipo(primera.getTipo());
            agrupado.setManual(false);
            salida.add(agrupado);
            puesta = true;
        }
        return salida;
    }
}
