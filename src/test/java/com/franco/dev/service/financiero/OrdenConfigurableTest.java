package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class OrdenConfigurableTest {

    private static final Function<Long, Long> ID = x -> x;

    @Test
    void sin_orden_queda_por_respaldo() {
        assertEquals(Arrays.asList(1L, 2L, 3L),
                OrdenConfigurable.ordenar(Arrays.asList(3L, 1L, 2L), ID, null, Comparator.naturalOrder()));
    }

    @Test
    void con_orden_y_uno_nuevo_al_final() {
        List<Long> out = OrdenConfigurable.ordenar(Arrays.asList(1L, 2L, 3L, 4L), ID, "[3,1]", Comparator.naturalOrder());
        assertEquals(Arrays.asList(3L, 1L, 2L, 4L), out);
    }

    @Test
    void orden_con_ids_que_ya_no_existen_se_ignora() {
        List<Long> out = OrdenConfigurable.ordenar(Arrays.asList(1L, 2L), ID, "[9,2]", Comparator.naturalOrder());
        assertEquals(Arrays.asList(2L, 1L), out);
    }

    @Test
    void validar_ids_normaliza_y_rechaza_basura_y_repetidos() {
        assertEquals("[3,1,2]", OrdenConfigurable.validarIds("[ 3, 1,2 ]", "x"));
        assertEquals("[]", OrdenConfigurable.validarIds("[]", "x"));
        assertNull(OrdenConfigurable.validarIds(null, "x"));
        assertThrows(GraphQLException.class, () -> OrdenConfigurable.validarIds("[1,a]", "x"));
        assertThrows(GraphQLException.class, () -> OrdenConfigurable.validarIds("[1,1]", "x"));
    }

    @Test
    void validar_enum_solo_acepta_valores_del_enum_sin_repetir() {
        assertEquals("[\"DEPOSITO_BANCARIO\",\"CAMBIO_DIVISA\"]", OrdenConfigurable.validarEnum(
                "[\"DEPOSITO_BANCARIO\", \"CAMBIO_DIVISA\"]", TipoOperacionFinanciera.class, "x"));
        assertThrows(GraphQLException.class, () -> OrdenConfigurable.validarEnum(
                "[\"PAGO_INVENTADO\"]", TipoOperacionFinanciera.class, "x"));
        assertThrows(GraphQLException.class, () -> OrdenConfigurable.validarEnum(
                "[\"CAMBIO_DIVISA\",\"CAMBIO_DIVISA\"]", TipoOperacionFinanciera.class, "x"));
    }
}
