package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ComprobanteSerie;
import com.franco.dev.repository.financiero.ComprobanteSerieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Numeración correlativa de comprobantes (CN3). */
class ComprobanteSerieServiceTest {

    private ComprobanteSerieRepository repository;
    private ComprobanteSerieService service;

    @BeforeEach
    void setUp() {
        repository = mock(ComprobanteSerieRepository.class);
        service = new ComprobanteSerieService(repository);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ComprobanteSerie serie(String prefijo, long siguiente, int relleno) {
        ComprobanteSerie s = new ComprobanteSerie();
        s.setTipo("ENTRADA_VARIA");
        s.setPrefijo(prefijo);
        s.setSiguiente(siguiente);
        s.setRellenoCeros(relleno);
        s.setActivo(true);
        return s;
    }

    @Test
    void numera_con_prefijo_y_relleno_e_incrementa() {
        ComprobanteSerie s = serie("EV-", 7, 5);
        when(repository.lockByTipo("ENTRADA_VARIA")).thenReturn(Optional.of(s));

        assertEquals("EV-00007", service.siguienteNumero("ENTRADA_VARIA"));
        assertEquals(8L, s.getSiguiente());
    }

    @Test
    void sin_relleno_ni_prefijo() {
        ComprobanteSerie s = serie("", 42, 0);
        when(repository.lockByTipo("ENTRADA_VARIA")).thenReturn(Optional.of(s));
        assertEquals("42", service.siguienteNumero("ENTRADA_VARIA"));
    }

    @Test
    void tipo_sin_serie_devuelve_null() {
        when(repository.lockByTipo("GASTO")).thenReturn(Optional.empty());
        assertNull(service.siguienteNumero("GASTO"));
    }

    @Test
    void serie_inactiva_devuelve_null() {
        ComprobanteSerie s = serie("X", 1, 0);
        s.setActivo(false);
        when(repository.lockByTipo("ENTRADA_VARIA")).thenReturn(Optional.of(s));
        assertNull(service.siguienteNumero("ENTRADA_VARIA"));
    }
}
