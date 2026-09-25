package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.graphql.activos.input.EnteInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code saveEnte} usaba el bean {@code modelMapper()} de la aplicacion, que tiene
 * {@code setFieldMatchingEnabled(true)} con acceso a campos privados. Para llenar
 * {@code Ente.creadoEn} entraba por reflexion a los campos internos de
 * {@code java.time.LocalDateTime}, y en JDK 17 eso lanza
 * {@code InaccessibleObjectException: module java.base does not "opens java.time"}.
 *
 * El efecto era que dar de alta un ente fallaba con CUALQUIER input, incluso uno vacio,
 * tanto desde el escritorio como desde la PWA.
 */
class EnteInputMappingTest {

    @Test
    void mapeaUnEnteNuevoSinTocarLaReflexionDeLocalDateTime() {
        EnteInput input = new EnteInput();
        input.setTipoEnte("VEHICULO");
        input.setReferenciaId(88L);
        input.setActivo(true);
        input.setUsuarioId(410L);

        Ente e = EnteGraphQL.strictMapper().map(input, Ente.class);

        assertEquals(88L, e.getReferenciaId());
        assertTrue(e.getActivo());
        // El resolver resuelve tipoEnte y usuario a mano contra el enum y el service.
        assertNull(e.getUsuario());
    }

    @Test
    void unInputVacioTampocoRompe() {
        // Es el caso que delataba el bug: fallaba incluso sin ningun campo cargado,
        // porque la reflexion sobre `creadoEn` no depende de lo que traiga el input.
        Ente e = EnteGraphQL.strictMapper().map(new EnteInput(), Ente.class);

        assertNull(e.getId());
        assertNull(e.getReferenciaId());
    }

    @Test
    void elTipoDeEnteLlegaDesdeElStringDelInput() {
        // TipoEnte es un enum en el dominio y un String en el input: la conversion
        // tiene que seguir funcionando con matching STRICT.
        EnteInput input = new EnteInput();
        input.setTipoEnte("INMUEBLE");

        Ente e = EnteGraphQL.strictMapper().map(input, Ente.class);

        assertEquals(TipoEnte.INMUEBLE, e.getTipoEnte());
    }
}
