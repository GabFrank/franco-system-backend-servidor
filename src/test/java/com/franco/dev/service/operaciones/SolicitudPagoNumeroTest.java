package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.CambioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Número de las solicitudes de pago: lo da la secuencia, no el conteo, y la secuencia se deja lista al
 * arrancar sin hacerla retroceder nunca.
 */
class SolicitudPagoNumeroTest {

    private SolicitudPagoRepository repository;
    private SolicitudPagoService service;
    private JdbcTemplate jdbc;
    private SolicitudPagoNumeroVerificador verificador;

    @BeforeEach
    void setUp() {
        repository = mock(SolicitudPagoRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new SolicitudPagoService(repository, mock(SolicitudPagoNotaRecepcionService.class),
                mock(NotaRecepcionRepository.class), mock(ProcesoEtapaService.class),
                mock(RecepcionMercaderiaNotaService.class), mock(RecepcionMercaderiaService.class),
                mock(MonedaRepository.class), mock(FormaPagoRepository.class), mock(CambioService.class));
        jdbc = mock(JdbcTemplate.class);
        verificador = new SolicitudPagoNumeroVerificador(jdbc);
    }

    private void secuenciaEn(long proximo, long minimo) {
        when(jdbc.queryForObject(SolicitudPagoNumeroVerificador.PROXIMO, Long.class)).thenReturn(proximo);
        when(jdbc.queryForObject(SolicitudPagoNumeroVerificador.MINIMO, Long.class)).thenReturn(minimo);
    }

    @Test
    void una_solicitud_nueva_toma_su_numero_de_la_secuencia_y_ya_no_cuenta_las_que_hay() {
        when(repository.siguienteNumero()).thenReturn(1365L);

        SolicitudPago guardada = service.save(new SolicitudPago());

        assertEquals("SP-001365", guardada.getNumeroSolicitud());
        verify(repository, never()).count();
    }

    @Test
    void el_formato_no_se_corta_al_pasar_los_seis_digitos() {
        when(repository.siguienteNumero()).thenReturn(1234567L);

        assertEquals("SP-1234567", service.save(new SolicitudPago()).getNumeroSolicitud());
    }

    @Test
    void una_solicitud_que_ya_existe_o_que_trae_numero_no_consume_otro() {
        SolicitudPago existente = new SolicitudPago();
        existente.setId(18L);
        existente.setNumeroSolicitud("SP-000018");
        service.save(existente);

        SolicitudPago conNumero = new SolicitudPago();
        conNumero.setNumeroSolicitud("IT-CPP");
        assertEquals("IT-CPP", service.save(conNumero).getNumeroSolicitud());

        verify(repository, never()).siguienteNumero();
    }

    @Test
    void si_la_secuencia_no_da_un_numero_el_alta_falla_en_vez_de_guardar_un_numero_inventado() {
        when(repository.siguienteNumero()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.save(new SolicitudPago()));
        verify(repository, never()).save(any());
    }

    @Test
    void al_arrancar_la_secuencia_se_crea_si_falta_y_no_se_toca_si_esta_al_dia() {
        secuenciaEn(1365, 1365);

        verificador.alinear();

        verify(jdbc).execute(SolicitudPagoNumeroVerificador.CREAR);
        verify(jdbc, never()).queryForObject(eq(SolicitudPagoNumeroVerificador.ADELANTAR), eq(Long.class), any());
    }

    @Test
    void si_quedo_detras_del_numero_mas_alto_se_adelanta_hasta_el_siguiente() {
        // Rollback del JAR: el anterior siguió numerando por conteo hasta el 1370 y la secuencia quedó en 1366.
        secuenciaEn(1366, 1371);

        verificador.alinear();

        verify(jdbc).queryForObject(SolicitudPagoNumeroVerificador.ADELANTAR, Long.class, 1371L);
    }

    @Test
    void con_huecos_la_secuencia_va_adelante_del_numero_mas_alto_y_no_se_la_hace_retroceder() {
        secuenciaEn(1400, 1371);

        verificador.alinear();

        verify(jdbc, never()).queryForObject(eq(SolicitudPagoNumeroVerificador.ADELANTAR), eq(Long.class), any());
    }

    @Test
    void si_no_se_puede_consultar_no_frena_el_arranque() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("sin base"))
                .when(jdbc).execute(SolicitudPagoNumeroVerificador.CREAR);

        assertDoesNotThrow(() -> verificador.alinear());
    }
}
