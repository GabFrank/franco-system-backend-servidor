package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.TipoCombustible;
import com.franco.dev.graphql.activos.input.TipoCombustibleInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mismo bug que {@link EnteInputMappingTest}: {@code saveTipoCombustible} usaba el bean
 * {@code modelMapper()} de la aplicacion, que con {@code setFieldMatchingEnabled(true)}
 * entraba por reflexion a los campos internos de {@code java.time.LocalDateTime} para
 * llenar {@code TipoCombustible.creadoEn}. En JDK 17 eso lanza
 * {@code InaccessibleObjectException} y guardar fallaba con cualquier input.
 */
class TipoCombustibleInputMappingTest {

    @Test
    void mapeaUnTipoDeCombustibleNuevo() {
        TipoCombustibleInput input = new TipoCombustibleInput();
        input.setDescripcion("DIESEL");
        input.setUsuarioId(410L);

        TipoCombustible e = TipoCombustibleGraphQL.strictMapper().map(input, TipoCombustible.class);

        assertEquals("DIESEL", e.getDescripcion());
        // usuarioId lo resuelve el resolver contra el service, no el mapper.
        assertNull(e.getUsuario());
    }

    @Test
    void unInputVacioTampocoRompe() {
        // El caso que delataba el bug: la reflexion sobre `creadoEn` no depende
        // de lo que traiga el input.
        TipoCombustible e = TipoCombustibleGraphQL.strictMapper()
                .map(new TipoCombustibleInput(), TipoCombustible.class);

        assertNull(e.getId());
        assertNull(e.getDescripcion());
    }
}
