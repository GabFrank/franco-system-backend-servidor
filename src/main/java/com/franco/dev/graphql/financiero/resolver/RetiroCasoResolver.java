package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.service.financiero.RetiroService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * El caso guarda {@code retiroId} suelto, no la entidad: el retiro es un documento replicado
 * desde la filial y engancharlo por FK ataría una tabla central-only al ciclo de replicación.
 *
 * <p>Pero el que investiga necesita ver el otro lado — quién hizo el retiro y de qué caja salió —
 * porque la diferencia es entre dos versiones y el caso solo trae la de tesorería. Se resuelve
 * acá, bajo demanda, en vez de duplicar los datos del cajero dentro del caso.
 */
@Component
public class RetiroCasoResolver implements GraphQLResolver<RetiroCaso> {

    @Autowired
    private RetiroService retiroService;

    public Retiro retiro(RetiroCaso e) {
        if (e.getRetiroId() == null || e.getSucursalId() == null) return null;
        return retiroService.findByIdAndSucursalId(e.getRetiroId(), e.getSucursalId());
    }
}
