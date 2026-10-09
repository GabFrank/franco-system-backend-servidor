package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tope de antigüedad para anular (CN4, issue #370). */
class LimiteAnulacionServiceTest {

    private ConfiguracionGeneralRepository configRepository;
    private LimiteAnulacionService service;

    @BeforeEach
    void setUp() {
        configRepository = mock(ConfiguracionGeneralRepository.class);
        service = new LimiteAnulacionService(configRepository);
    }

    private void conLimite(Integer dias) {
        ConfiguracionGeneral config = new ConfiguracionGeneral();
        config.setDiasLimiteAnulacion(dias);
        when(configRepository.findAll()).thenReturn(Collections.singletonList(config));
    }

    private static LocalDateTime hace(int dias) {
        return LocalDateTime.now().minusDays(dias);
    }

    @Test
    void mas_viejo_que_el_limite_se_rechaza_nombrando_el_documento_y_su_fecha() {
        conLimite(5);
        LocalDateTime fecha = hace(9);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.requireDentroDelLimite(fecha, "El pago #12"));

        assertEquals("El pago #12 del " + fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " supera el límite de 5 días para anular.", e.getMessage());
    }

    @Test
    void dentro_del_limite_pasa() {
        conLimite(5);
        assertDoesNotThrow(() -> service.requireDentroDelLimite(hace(4), "El pago #12"));
    }

    @Test
    void sin_fila_de_configuracion_no_hay_tope() {
        when(configRepository.findAll()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> service.requireDentroDelLimite(hace(900), "El pago #12"));
    }

    @Test
    void con_el_limite_nulo_o_en_cero_no_hay_tope() {
        conLimite(null);
        assertDoesNotThrow(() -> service.requireDentroDelLimite(hace(900), "El pago #12"));
        conLimite(0);
        assertDoesNotThrow(() -> service.requireDentroDelLimite(hace(900), "El pago #12"));
    }

    @Test
    void una_fecha_nula_no_se_puede_medir_y_pasa() {
        conLimite(5);
        assertDoesNotThrow(() -> service.requireDentroDelLimite(null, "El pago #12"));
    }

    @Test
    void si_no_se_puede_leer_la_configuracion_la_falla_no_se_esconde() {
        when(configRepository.findAll()).thenThrow(new RuntimeException("sin base"));
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.requireDentroDelLimite(hace(900), "El pago #12"));
        assertEquals("sin base", e.getMessage());
    }
}
