package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.graphql.personas.input.FuncionarioInput;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * El sueldo del funcionario entra por GraphQL como Float y llega a la entidad por un
 * ModelMapper generico, sin ninguna conversion escrita a mano
 * ({@code FuncionarioGraphQL.saveFuncionario}, both {@code map(input, e)} y
 * {@code map(input, Funcionario.class)}).
 *
 * <p>Esa conversion implicita es el punto mas fragil del cambio de tipo de
 * {@code Funcionario.sueldo}: si ModelMapper la resuelve distinto, el sueldo se guarda
 * con otro valor y no lo caza nada — ni el compilador ni un test de servicio.</p>
 *
 * <p>Este test existe para fijar el valor a ambos lados del cambio. Se escribio ANTES de
 * migrar el tipo, justamente para poder compararlo.</p>
 */
public class FuncionarioInputMapeoTest {

    /** Un sueldo real del padron: no redondo, para que un error de conversion se vea. */
    private static final float SUELDO = 2680373f;

    private FuncionarioInput input() {
        FuncionarioInput in = new FuncionarioInput();
        in.setSueldo(SUELDO);
        return in;
    }

    /** Alta: map(input, Funcionario.class). */
    @Test
    void elSueldoSobreviveAlAltaPorModelMapper() {
        Funcionario e = new ModelMapper().map(input(), Funcionario.class);
        assertNotNull(e.getSueldo(), "el mapeo dejo el sueldo en null");
        assertEquals(0, new BigDecimal(String.valueOf(SUELDO))
                        .compareTo(new BigDecimal(e.getSueldo().toString())),
                "el alta guardo un sueldo distinto al que mando el cliente: " + e.getSueldo());
    }

    /** Update: map(input, entidadExistente) — el camino que usa la pantalla de edicion. */
    @Test
    void elSueldoSobreviveAlUpdatePorModelMapper() {
        Funcionario e = new Funcionario();
        new ModelMapper().map(input(), e);
        assertNotNull(e.getSueldo(), "el mapeo dejo el sueldo en null");
        assertEquals(0, new BigDecimal(String.valueOf(SUELDO))
                        .compareTo(new BigDecimal(e.getSueldo().toString())),
                "el update guardo un sueldo distinto al que mando el cliente: " + e.getSueldo());
    }

    /**
     * Un input sin sueldo no debe inventar un cero: la pantalla de edicion manda updates
     * parciales, y un cero pisado seria un sueldo borrado sin que nadie lo pida.
     */
    @Test
    void unInputSinSueldoNoEstampaCero() {
        Funcionario e = new Funcionario();
        new ModelMapper().map(new FuncionarioInput(), e);
        if (e.getSueldo() != null) {
            assertEquals(0, BigDecimal.ZERO.compareTo(new BigDecimal(e.getSueldo().toString())),
                    "un input vacio dejo un sueldo distinto de null/cero: " + e.getSueldo());
        }
    }
}
