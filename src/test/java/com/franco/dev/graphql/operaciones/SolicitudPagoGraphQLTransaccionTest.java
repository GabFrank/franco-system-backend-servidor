package com.franco.dev.graphql.operaciones;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda de #435: estas mutations llaman a varios servicios y tienen que comitear juntas. La atomicidad la prueba
 * SolicitudPagoAtomicidadIT contra la base; esto solo evita que alguien saque la anotación sin que el CI lo note.
 */
class SolicitudPagoGraphQLTransaccionTest {

    // Las tres últimas validan el estado con lock: la anotación hace que el lock dure hasta el cambio.
    private static final List<String> MUTATIONS = Arrays.asList("saveSolicitudPago", "actualizarSolicitudPago",
            "removerNotaDeSolicitudPago", "eliminarSolicitudPagoDetalle", "agregarSolicitudPagoDetalle");

    @Test
    void lasMutationsCompuestasSonTransaccionales() {
        for (String nombre : MUTATIONS) {
            Method m = Arrays.stream(SolicitudPagoGraphQL.class.getDeclaredMethods())
                    .filter(x -> x.getName().equals(nombre)).findFirst()
                    .orElseThrow(() -> new AssertionError("no existe " + nombre));
            assertTrue(m.isAnnotationPresent(Transactional.class), nombre + " perdió @Transactional");
        }
    }
}
