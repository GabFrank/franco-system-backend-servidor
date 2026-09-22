package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FormatoTerminalPos;
import com.franco.dev.repository.financiero.FormatoTerminalPosRepository;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El tipo declarado en el mapeo se valida en el alta del formato, que es el unico lugar donde
 * todavia hay a quien avisarle.
 *
 * <p>La derivacion lo parsea por regex y un typo no matchea: el campo queda sin tipo y el
 * administrador que creyo estar declarando una defensa se queda sin ella, en silencio, hasta que
 * alguien note que un campo mal leido nunca va a revision. Estos casos fijan que el error salte
 * antes de guardar.
 */
public class FormatoTerminalPosMapeoTipoTest {

    private static final String PATRON =
            "^[\\s\\S]*AUT:\\s*(?<auth>[A-Z0-9]+)[\\s\\S]*MONTO:\\s*(?<monto>[0-9.]+)[\\s\\S]*$";
    private static final String EJEMPLO = "AUT: D380AD\nMONTO: 150.000";

    private FormatoTerminalPosService service;

    @BeforeEach
    public void setUp() {
        FormatoTerminalPosRepository repository = mock(FormatoTerminalPosRepository.class);
        when(repository.save(org.mockito.ArgumentMatchers.<FormatoTerminalPos>any()))
                .thenAnswer(i -> i.getArgument(0));
        service = new FormatoTerminalPosService(repository, mock(TerminalPosRepository.class));
    }

    private FormatoTerminalPos formato(String mapeo) {
        FormatoTerminalPos f = new FormatoTerminalPos();
        f.setNombre("Bancard v5.2");
        f.setTipo(FormatoTerminalPos.TIPO_MAQUINA);
        f.setPatron(PATRON);
        f.setEjemplo(EJEMPLO);
        f.setMapeo(mapeo);
        return f;
    }

    @Test
    public void un_tipo_valido_se_guarda() {
        assertNotNull(service.save(formato(
                "{\"codigoAutorizacion\":{\"de\":\"auth\",\"tipo\":\"TEXTO\"},"
                + "\"monto\":{\"de\":\"monto\",\"tipo\":\"NUMERO\"}}")));
    }

    @Test
    public void sin_tipo_declarado_se_guarda_igual() {
        // Declarar el tipo es opcional a proposito: declarar de mas manda a revision lecturas
        // correctas, declarar de menos solo pierde una defensa.
        assertNotNull(service.save(formato("{\"codigoAutorizacion\":{\"de\":\"auth\"}}")));
    }

    @Test
    public void un_tipo_que_no_es_ninguno_de_los_tres_se_rechaza() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.save(formato(
                "{\"codigoAutorizacion\":{\"de\":\"auth\",\"tipo\":\"ALFANUMERICO\"}}")));
        assertTrue(e.getMessage().contains("ALFANUMERICO"), e.getMessage());
        assertTrue(e.getMessage().contains("TEXTO"), "hay que decirle cuales valen: " + e.getMessage());
    }

    @Test
    public void el_typo_clasico_cero_por_O_se_rechaza() {
        // Sin esta validacion "NUMER0" se degrada a null y no lo nota nadie.
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.save(formato(
                "{\"monto\":{\"de\":\"monto\",\"tipo\":\"NUMER0\"}}")));
        assertTrue(e.getMessage().contains("NUMER0"), e.getMessage());
    }

    @Test
    public void un_tipo_valido_que_la_derivacion_no_va_a_leer_se_rechaza() {
        // Valido para Jackson, invisible para el regex de la derivacion: el objeto anidado corta
        // el matcheo. El mapeo se guardaria "bien" y el campo quedaria sin tipo.
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.save(formato(
                "{\"monto\":{\"de\":\"monto\",\"mapa\":{\"A\":\"B\"},\"tipo\":\"NUMERO\"}}")));
        assertTrue(e.getMessage().contains("no va a leer"), e.getMessage());
    }

    @Test
    public void en_minuscula_se_acepta_porque_la_derivacion_normaliza() {
        assertNotNull(service.save(formato("{\"monto\":{\"de\":\"monto\",\"tipo\":\"numero\"}}")));
    }
}
