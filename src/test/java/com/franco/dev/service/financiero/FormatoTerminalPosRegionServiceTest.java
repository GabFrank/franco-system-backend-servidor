package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.domain.financiero.FormatoTerminalPosRegion;
import com.franco.dev.repository.financiero.FormatoTerminalPosRegionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Fija las reglas del mapa.
 *
 * <p>Los casos que mas importan son los de sobrescritura: la derivacion se dispara como accion del
 * usuario, o sea que es repetible, y la tabla se replica MAIN_TO_ALL. Una sobrescritura mala no
 * queda local: baja a las 24 filiales. Y una region MANUAL es lo mas caro del modulo --alguien miro
 * un cupon y la arreglo-- asi que ninguna corrida puede llevarsela puesta.
 */
public class FormatoTerminalPosRegionServiceTest {

    private static final String MAPEO =
            "{\"monto\":{\"de\":\"monto\",\"escala\":2,\"mapa\":{\"A\":\"B\"}},"
            + "\"codigoAutorizacion\":{\"de\":\"auth\"},"
            + "\"terminal\":{\"de\":\"term\"}}";

    private FormatoTerminalPosRegionRepository repository;
    private FormatoTerminalPosRegionService service;
    private FormatoTerminalPos formato;

    @BeforeEach
    public void setUp() {
        repository = mock(FormatoTerminalPosRegionRepository.class);
        service = new FormatoTerminalPosRegionService(repository);
        formato = new FormatoTerminalPos();
        formato.setId(7L);
        formato.setNombre("Bancard v5.2");
        formato.setTipo(FormatoTerminalPos.TIPO_MAQUINA);
        formato.setMapeo(MAPEO);
    }

    private FormatoTerminalPosRegion region(String campo, String etiqueta, double x1, double y1,
                                            double x2, double y2) {
        FormatoTerminalPosRegion r = new FormatoTerminalPosRegion();
        r.setFormatoTerminalPos(formato);
        r.setCampo(campo);
        r.setEtiqueta(etiqueta);
        r.setPosicion(FormatoTerminalPosRegion.POSICION_DERECHA);
        r.setX1(BigDecimal.valueOf(x1));
        r.setY1(BigDecimal.valueOf(y1));
        r.setX2(BigDecimal.valueOf(x2));
        r.setY2(BigDecimal.valueOf(y2));
        return r;
    }

    private void conRegionesExistentes(FormatoTerminalPosRegion... rs) {
        when(repository.findByFormatoTerminalPosIdOrderByOrdenAscIdAsc(7L))
                .thenReturn(new ArrayList<FormatoTerminalPosRegion>(Arrays.asList(rs)));
        for (FormatoTerminalPosRegion r : rs) {
            when(repository.findByFormatoTerminalPosIdAndCampo(7L, r.getCampo())).thenReturn(r);
        }
    }

    // ── Validaciones de una region suelta ───────────────────────────────────────────────────

    @Test
    public void un_formato_web_no_lleva_mapa() {
        // WEB es patron puro: la cadena entra por el lector y no hay imagen donde ubicar regiones.
        // Guardar un mapa ahi da la falsa impresion de haber configurado algo que no se aplica.
        formato.setTipo(FormatoTerminalPos.TIPO_WEB);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.save(region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2)));

        assertTrue(e.getMessage().contains("MAQUINA"), e.getMessage());
    }

    @Test
    public void un_campo_que_el_mapeo_no_produce_se_rechaza() {
        // No es solo peso muerto: la region ACOTA el reconocimiento a una zona por un campo que
        // nadie va a leer.
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.save(region("numeroBoleta", "BOLETA", 0.1, 0.1, 0.5, 0.2)));

        assertTrue(e.getMessage().contains("no produce el campo"), e.getMessage());
    }

    @Test
    public void una_clave_anidada_del_mapeo_no_cuenta_como_campo() {
        // El mapeo de arriba tiene un "mapa" ANIDADO dentro de monto. Un regex sobre las claves lo
        // tomaria como campo de primer nivel y aceptaria una region para el. Por eso se parsea.
        assertThrows(GraphQLException.class,
                () -> service.save(region("mapa", "MAPA", 0.1, 0.1, 0.5, 0.2)));
    }

    @Test
    public void media_caja_no_es_una_pista() {
        FormatoTerminalPosRegion r = region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2);
        r.setY2(null);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.save(r));

        assertTrue(e.getMessage().contains("cuatro coordenadas"), e.getMessage());
    }

    @Test
    public void una_caja_al_reves_se_rechaza() {
        assertThrows(GraphQLException.class,
                () -> service.save(region("monto", "MONTO", 0.6, 0.1, 0.2, 0.2)));
    }

    @Test
    public void las_coordenadas_van_normalizadas() {
        // Un pixel crudo --450 en vez de 0.45-- es el error tipico al portar el mapa desde una
        // herramienta que trabaja en px.
        assertThrows(GraphQLException.class,
                () -> service.save(region("monto", "MONTO", 0.1, 0.1, 450, 0.2)));
    }

    @Test
    public void sin_etiqueta_y_sin_zona_la_region_no_dice_nada() {
        FormatoTerminalPosRegion r = new FormatoTerminalPosRegion();
        r.setFormatoTerminalPos(formato);
        r.setCampo("monto");

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.save(r));

        assertTrue(e.getMessage().contains("no tiene etiqueta ni zona"), e.getMessage());
    }

    @Test
    public void con_etiqueta_y_sin_posicion_se_asume_a_la_derecha() {
        // "AUT:" sin posicion no dice si el valor esta al lado o en el renglon de abajo. Se asume
        // el caso abrumadoramente mas comun en un ticket en vez de rechazar.
        FormatoTerminalPosRegion r = region("codigoAutorizacion", "AUT:", 0.1, 0.1, 0.5, 0.2);
        r.setPosicion(null);

        service.save(r);

        assertEquals(FormatoTerminalPosRegion.POSICION_DERECHA, r.getPosicion());
    }

    @Test
    public void una_region_solo_por_etiqueta_es_valida() {
        // Mapa parcial: sin zona, el campo se resuelve por etiqueta sobre el texto ya reconocido.
        // Es preferible a inventar una zona, que despues hace desaparecer un campo.
        FormatoTerminalPosRegion r = new FormatoTerminalPosRegion();
        r.setFormatoTerminalPos(formato);
        r.setCampo("terminal");
        r.setEtiqueta("TERM");

        service.save(r);

        assertFalse(r.tieneCaja());
        verify(repository).save(r);
    }

    // ── Sobrescritura ───────────────────────────────────────────────────────────────────────

    @Test
    public void sobre_un_formato_sin_mapa_guarda_sin_preguntar() {
        conRegionesExistentes();

        FormatoTerminalPosRegionService.ResultadoDerivacion r = service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2)), false);

        assertTrue(r.aplicado);
        assertEquals(1, r.creadas);
        // El diff se llena igual, con las altas: el desktop muestra lo que quedo configurado sin
        // tener que volver a consultar.
        assertEquals(1, r.cambios.size());
        assertTrue(r.cambios.get(0).contains("se agrega"), r.cambios.get(0));
    }

    @Test
    public void sobre_un_mapa_existente_no_pisa_sin_confirmacion() {
        FormatoTerminalPosRegion vieja = region("monto", "IMPORTE", 0.1, 0.1, 0.5, 0.2);
        vieja.setId(1L);
        conRegionesExistentes(vieja);

        FormatoTerminalPosRegionService.ResultadoDerivacion r = service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.3, 0.1, 0.7, 0.2)), false);

        assertFalse(r.aplicado);
        assertEquals(1, r.cambios.size());
        assertTrue(r.cambios.get(0).contains("monto"), r.cambios.get(0));
        verify(repository, never()).save(any(FormatoTerminalPosRegion.class));
    }

    @Test
    public void con_la_confirmacion_si_pisa() {
        FormatoTerminalPosRegion vieja = region("monto", "IMPORTE", 0.1, 0.1, 0.5, 0.2);
        vieja.setId(1L);
        conRegionesExistentes(vieja);

        FormatoTerminalPosRegionService.ResultadoDerivacion r = service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.3, 0.1, 0.7, 0.2)), true);

        assertTrue(r.aplicado);
        assertEquals(1, r.actualizadas);
        assertEquals("MONTO", vieja.getEtiqueta(), "se copia sobre la fila existente");
        assertEquals(FormatoTerminalPosRegion.ORIGEN_DERIVADA, vieja.getOrigen());
    }

    @Test
    public void una_region_MANUAL_no_se_toca_ni_con_la_confirmacion() {
        // El caso que mas importa. Alguien corrigio esta region mirando un cupon; el plan mismo
        // anticipa esa correccion cuando dice que el editor vuelve para arreglar mapas torcidos.
        // Perderla con un clic haria inutil haberla hecho.
        FormatoTerminalPosRegion manual = region("monto", "CORREGIDO A MANO", 0.1, 0.1, 0.5, 0.2);
        manual.setId(1L);
        manual.setOrigen(FormatoTerminalPosRegion.ORIGEN_MANUAL);
        conRegionesExistentes(manual);

        FormatoTerminalPosRegionService.ResultadoDerivacion r = service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.3, 0.1, 0.7, 0.2)), true);

        assertTrue(r.aplicado);
        assertEquals(0, r.actualizadas);
        assertEquals(Collections.singletonList("monto"), r.conservadasManuales);
        assertEquals("CORREGIDO A MANO", manual.getEtiqueta(), "la correccion sobrevive");
        verify(repository, never()).save(any(FormatoTerminalPosRegion.class));
    }

    @Test
    public void una_derivada_que_el_patron_ya_no_produce_se_borra() {
        // Si sobrevive, sigue acotando el reconocimiento a una zona por un campo que ya no existe.
        FormatoTerminalPosRegion huerfana = region("terminal", "TERM", 0.1, 0.8, 0.5, 0.9);
        huerfana.setId(2L);
        conRegionesExistentes(huerfana);

        FormatoTerminalPosRegionService.ResultadoDerivacion r = service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2)), true);

        assertEquals(1, r.eliminadas);
        assertEquals(1, r.creadas);
        verify(repository).delete(huerfana);
    }

    @Test
    public void una_MANUAL_no_se_borra_aunque_el_patron_ya_no_la_produzca() {
        FormatoTerminalPosRegion manual = region("terminal", "TERM", 0.1, 0.8, 0.5, 0.9);
        manual.setId(2L);
        manual.setOrigen(FormatoTerminalPosRegion.ORIGEN_MANUAL);
        conRegionesExistentes(manual);

        service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2)), true);

        verify(repository, never()).delete(any(FormatoTerminalPosRegion.class));
    }

    @Test
    public void derivar_dos_veces_el_mismo_mapa_no_reporta_cambios() {
        // La derivacion es deterministica por construccion: no hay estado acumulado.
        FormatoTerminalPosRegion vieja = region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2);
        vieja.setId(1L);
        conRegionesExistentes(vieja);

        FormatoTerminalPosRegionService.ResultadoDerivacion r = service.guardarDerivadas(formato,
                Collections.singletonList(region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2)), false);

        assertFalse(r.aplicado, "igual pide confirmacion: ya hay mapa");
        assertTrue(r.cambios.isEmpty(), "pero el diff tiene que venir vacio");
    }

    @Test
    public void una_propuesta_vacia_no_borra_el_mapa() {
        // Un patron que dejo de matchear devolveria cero regiones. Aplicarlo vaciaria el mapa de
        // las 24 filiales de un saque.
        conRegionesExistentes(region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2));

        assertThrows(GraphQLException.class,
                () -> service.guardarDerivadas(formato, Collections.<FormatoTerminalPosRegion>emptyList(), true));
    }

    @Test
    public void una_propuesta_invalida_no_se_guarda_a_medias() {
        // Se valida todo antes de escribir nada: media derivacion aplicada es peor que ninguna.
        conRegionesExistentes();
        List<FormatoTerminalPosRegion> propuestas = Arrays.asList(
                region("monto", "MONTO", 0.1, 0.1, 0.5, 0.2),
                region("numeroBoleta", "BOLETA", 0.1, 0.3, 0.5, 0.4));

        assertThrows(GraphQLException.class, () -> service.guardarDerivadas(formato, propuestas, true));

        verify(repository, never()).save(any(FormatoTerminalPosRegion.class));
    }
}
