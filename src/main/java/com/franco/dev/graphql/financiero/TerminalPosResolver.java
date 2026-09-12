package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.service.financiero.TerminalPosService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Los campos de {@code TerminalPos} que no salen directo de una columna.
 *
 * <p>{@code campos_obligatorios} se guarda como un JSON array en una columna de texto --replicada
 * MAIN_TO_ALL, donde una tabla auxiliar seria una tabla mas que sincronizar por un dato que siempre
 * se lee entero-- pero la API lo expone como lista, que es lo que el cliente necesita.
 */
@Component
public class TerminalPosResolver implements GraphQLResolver<TerminalPos> {

    @Autowired
    private TerminalPosService service;

    /** La lista cruda. null = no configurada para este aparato, hereda del formato. */
    public List<String> camposObligatorios(TerminalPos t) {
        return service.camposObligatoriosDe(t);
    }

    /**
     * Lo que se le exige de verdad al cajero: la lista por aparato si la hay, y si no, los que el
     * mapeo del formato declara obligatorios. Es con esto que se arma el formulario de carga a
     * mano.
     */
    public List<String> camposObligatoriosEfectivos(TerminalPos t) {
        return service.camposObligatoriosEfectivos(t);
    }
}
