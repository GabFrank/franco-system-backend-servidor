package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.dto.PreciosEnMonedaDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte importes (en guaranies, o en la moneda del precio) a las demas monedas, con la ultima
 * cotizacion cargada.
 *
 * Lo usa el kiosco de precios de la PWA. Antes de esto, `frc-mobile` multiplicaba el precio por
 * `1 / cambio` en el cliente, con el redondeo que le tocara al navegador.
 */
@Service
public class ConversionPrecioService {

    /** Para una moneda sin `decimales` cargados que no es el guarani. */
    private static final int DECIMALES_POR_DEFECTO = 2;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private CambioService cambioService;

    /**
     * Los importes en cada moneda activa con cotizacion, en el orden de los ids.
     *
     * Una moneda sin cotizacion valida (null o <= 0) se omite: mostrarla con un precio inventado
     * es peor que no ofrecerla. El guarani entra con cotizacion 1 y sus montos tal cual, asi el
     * cliente arma el selector con una sola lista.
     */
    public List<PreciosEnMonedaDto> convertir(List<Double> montosGs) {
        return convertir(montosGs, null);
    }

    /**
     * Lo mismo con los importes en {@code origen} (null = guaranies, lo de siempre): un precio en
     * dolares (V260.5) se lleva a cada moneda pasando por el guarani, con un solo redondeo al final.
     * Sin cotizacion del origen no se convierte nada: vacio.
     */
    public List<PreciosEnMonedaDto> convertir(List<Double> montos, Moneda origen) {
        List<PreciosEnMonedaDto> resultado = new ArrayList<>();
        if (montos == null) {
            return resultado;
        }
        BigDecimal gsPorUnidadOrigen = BigDecimal.ONE;
        if (origen != null && !MonedaService.esGuarani(origen)) {
            Double c = cambioService.findLastValorEnGsByMonedaId(origen.getId());
            if (c == null || c <= 0) {
                return resultado;
            }
            gsPorUnidadOrigen = BigDecimal.valueOf(c);
        }
        List<Double> montosGs = montos;
        for (Moneda moneda : monedaService.findAll2()) {
            if (Boolean.FALSE.equals(moneda.getActivo())) {
                continue;
            }
            Double cambio = cambioService.findLastValorEnGsByMonedaId(moneda.getId());
            if (cambio == null || cambio <= 0) {
                continue;
            }
            int decimales = decimalesDe(moneda, cambio);
            BigDecimal divisor = BigDecimal.valueOf(cambio);
            List<Double> enMoneda = new ArrayList<>();
            for (Double monto : montosGs) {
                enMoneda.add(monto == null
                        ? null
                        : BigDecimal.valueOf(monto).multiply(gsPorUnidadOrigen)
                            .divide(divisor, decimales, RoundingMode.HALF_UP).doubleValue());
            }
            resultado.add(new PreciosEnMonedaDto(moneda, decimales, enMoneda));
        }
        return resultado;
    }

    private int decimalesDe(Moneda moneda, double cambio) {
        if (moneda.getDecimales() != null && moneda.getDecimales() >= 0) {
            return moneda.getDecimales();
        }
        // El guarani no tiene centimos: su cotizacion contra si mismo es 1.
        return cambio == 1d ? 0 : DECIMALES_POR_DEFECTO;
    }
}
