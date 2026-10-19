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
import java.time.LocalDateTime;

/**
 * La del central es la del dialogo de administracion y pide rol. El cobro del PDV no pasa por aca:
 * lee la copia replicada en el filial, que no pide rol y funciona sin internet.
 */
@Component
@AllArgsConstructor
public class ConfiguracionCobroGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ConfiguracionCobroService service;
    private final TesoreriaSecurityService seg;

    public ConfiguracionCobro configuracionCobro() {
        seg.requireVer();
        return service.findOrDefault();
    }

    /** Semantica PATCH: un campo que no viene no pisa lo guardado. */
    public ConfiguracionCobro saveConfiguracionCobro(ConfiguracionCobroInput input) {
        seg.requireGestionar();
        BigDecimal pct = input.getAumentoTopePorcentaje();
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
