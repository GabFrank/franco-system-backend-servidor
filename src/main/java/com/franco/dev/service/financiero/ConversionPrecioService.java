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
 * Convierte importes en guaranies a las demas monedas, con la ultima cotizacion cargada.
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
        List<PreciosEnMonedaDto> resultado = new ArrayList<>();
        if (montosGs == null) {
            return resultado;
        }
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
            List<Double> montos = new ArrayList<>();
            for (Double monto : montosGs) {
                montos.add(monto == null
                        ? null
                        : BigDecimal.valueOf(monto).divide(divisor, decimales, RoundingMode.HALF_UP).doubleValue());
            }
            resultado.add(new PreciosEnMonedaDto(moneda, decimales, montos));
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
