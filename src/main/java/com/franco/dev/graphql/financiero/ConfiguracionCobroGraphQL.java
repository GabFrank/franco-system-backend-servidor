package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.graphql.financiero.input.ConfiguracionCobroInput;
import com.franco.dev.service.financiero.ConfiguracionCobroService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Lectura sin rol, escritura con TESORERIA GESTIONAR. El cobro del PDV lee la copia replicada en el
 * filial (funciona sin internet), pero el desktop web no tiene filial local y la pide aca con la sesion
 * del cajero: si pidiera rol, a ese cajero le regiria siempre el 20%. Solo expone el tope y el nickname
 * de quien lo guardo.
 */
@Component
@AllArgsConstructor
public class ConfiguracionCobroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ConfiguracionCobroService service;
    private final TesoreriaSecurityService seg;

    public ConfiguracionCobro configuracionCobro() {
        return service.findOrDefault();
    }

    /** Semantica PATCH: un campo que no viene no pisa lo guardado. */
    public ConfiguracionCobro saveConfiguracionCobro(ConfiguracionCobroInput input) {
        seg.requireGestionar();
        // A la escala de la columna (NUMERIC(6,2)) antes de validar: 0.004 pasaria el > 0 y se guardaria 0.00.
        BigDecimal pct = input.getAumentoTopePorcentaje() == null ? null
                : input.getAumentoTopePorcentaje().setScale(2, RoundingMode.HALF_UP);
        // Se valida aca y no se deja llegar al CHECK: la violacion subiria como un error opaco.
        if (pct != null && (pct.signum() <= 0 || pct.compareTo(CIEN) > 0)) {
            throw new GraphQLException("El tope del aumento es un porcentaje del total: tiene que ser mayor a 0"
                    + " y hasta 100. Para no limitar el aumento, elegí «sin tope».");
        }
        ConfiguracionCobro config = service.findOrNew();
        if (input.getAumentoConTope() != null) {
            config.setAumentoConTope(input.getAumentoConTope());
        }
        if (pct != null) {
            config.setAumentoTopePorcentaje(pct);
        }
        config.setUsuario(seg.currentUsuario());
        config.setModificadoEn(LocalDateTime.now());
        return service.save(config);
    }
}
