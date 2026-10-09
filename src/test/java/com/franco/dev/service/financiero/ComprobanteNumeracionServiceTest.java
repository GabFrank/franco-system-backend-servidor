package com.franco.dev.service.financiero;

import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Número de comprobante (issue #376): vacío es «sin número», lo tipeado no se repite, y la serie salta
 * los números que ya estén usados en vez de trabarse.
 */
class ComprobanteNumeracionServiceTest {

    private ComprobanteSerieService serie;
    private BloqueoTransaccionalService bloqueo;
    private ComprobanteNumeracionService service;
    private final Set<String> usados = new HashSet<>();
    private Predicate<String> usado;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        serie = mock(ComprobanteSerieService.class);
        bloqueo = mock(BloqueoTransaccionalService.class);
        service = new ComprobanteNumeracionService(serie, bloqueo);
        usado = mock(Predicate.class);
        when(usado.test(any())).thenAnswer(i -> usados.contains(i.<String>getArgument(0)));
    }

    private String resolver(String recibido) {
        return service.resolver("ENTRADA_VARIA", recibido, usado, "una entrada varia");
    }

    @Test
    void vacio_o_espacios_es_sin_numero_y_sin_serie_queda_nulo() {
        assertNull(resolver(""));
        assertNull(resolver("   "));
        assertNull(resolver(null));
        verify(serie, times(3)).siguienteNumero("ENTRADA_VARIA");
        verify(usado, never()).test(any());
    }

    @Test
    void vacio_con_serie_se_autonumera() {
        when(serie.siguienteNumero("ENTRADA_VARIA")).thenReturn("ev-0007");

        assertEquals("EV-0007", resolver(""));
    }

    @Test
    void lo_tipeado_se_guarda_sin_espacios_y_en_mayusculas_y_no_gasta_la_serie() {
        assertEquals("REC-12", resolver("  rec-12 "));
        verify(serie, never()).siguienteNumero(any());
    }

    @Test
    void lo_tipeado_que_ya_usa_otro_documento_se_rechaza() {
        usados.add("REC-12");

        GraphQLException e = assertThrows(GraphQLException.class, () -> resolver("rec-12"));

        assertTrue(e.getMessage().contains("Ya existe una entrada varia con el comprobante REC-12"), e.getMessage());
    }

    @Test
    void el_lock_se_toma_antes_de_buscar_el_repetido_y_sobre_el_numero_normalizado() {
        resolver(" rec-12 ");

        InOrder orden = inOrder(bloqueo, usado);
        orden.verify(bloqueo).tomar("ENTRADA_VARIA:REC-12");
        orden.verify(usado).test("REC-12");
    }

    @Test
    void si_la_serie_genera_un_numero_ya_usado_salta_al_siguiente_en_vez_de_rechazar() {
        usados.add("EV-5");
        usados.add("EV-6");
        when(serie.siguienteNumero("ENTRADA_VARIA")).thenReturn("EV-5", "EV-6", "EV-7");

        assertEquals("EV-7", resolver(null));

        verify(serie, times(3)).siguienteNumero("ENTRADA_VARIA");
        InOrder orden = inOrder(bloqueo, usado);
        orden.verify(bloqueo).tomar("ENTRADA_VARIA:EV-5");
        orden.verify(usado).test("EV-5");
        orden.verify(bloqueo).tomar("ENTRADA_VARIA:EV-7");
        orden.verify(usado).test("EV-7");
    }

    @Test
    void una_serie_que_solo_da_numeros_usados_no_gira_para_siempre() {
        when(serie.siguienteNumero("ENTRADA_VARIA")).thenReturn("EV-1");
        usados.add("EV-1");

        GraphQLException e = assertThrows(GraphQLException.class, () -> resolver(null));

        assertTrue(e.getMessage().contains("números ya usados"), e.getMessage());
        verify(serie, times(ComprobanteNumeracionService.MAXIMO_DE_SALTOS + 1)).siguienteNumero("ENTRADA_VARIA");
    }

    @Test
    void un_comprobante_mas_largo_que_la_columna_se_rechaza_con_mensaje() {
        StringBuilder largo = new StringBuilder();
        for (int i = 0; i < 61; i++) largo.append('A');

        GraphQLException e = assertThrows(GraphQLException.class, () -> resolver(largo.toString()));

        assertTrue(e.getMessage().contains("hasta 60 caracteres"), e.getMessage());
        verify(bloqueo, never()).tomar(any());
    }

    @Test
    void cada_tipo_de_documento_tiene_su_propio_espacio_de_numeros() {
        service.resolver("OPERACION_FINANCIERA", "rec-12", usado, "una operación financiera");

        verify(bloqueo).tomar("OPERACION_FINANCIERA:REC-12");
    }
}
