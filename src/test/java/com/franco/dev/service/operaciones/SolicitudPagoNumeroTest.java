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
    void el_verificador_se_instancia_aunque_la_aplicacion_arranque_con_inicializacion_perezosa() {
        // spring.main.lazy-initialization=true: sin @Lazy(false) nadie lo pide y su @PostConstruct no corre.
        org.springframework.context.annotation.Lazy lazy =
                SolicitudPagoNumeroVerificador.class.getAnnotation(org.springframework.context.annotation.Lazy.class);
        assertNotNull(lazy);
        assertFalse(lazy.value());
    }

    @Test
    void al_arrancar_la_secuencia_se_crea_solo_si_falta() {
        when(jdbc.queryForObject(SolicitudPagoNumeroVerificador.EXISTE, Boolean.class)).thenReturn(true);
        verificador.alinear();
        verify(jdbc, never()).execute(SolicitudPagoNumeroVerificador.CREAR);

        when(jdbc.queryForObject(SolicitudPagoNumeroVerificador.EXISTE, Boolean.class)).thenReturn(false);
        verificador.alinear();
        verify(jdbc).execute(SolicitudPagoNumeroVerificador.CREAR);
    }

    @Test
    void adelantar_es_una_sola_sentencia_que_solo_actua_si_la_secuencia_esta_atras() {
        String sql = SolicitudPagoNumeroVerificador.ADELANTAR_SI_ATRASADA;

        // Lee el máximo, compara con lo que daría la secuencia y adelanta, todo en la misma sentencia.
        assertTrue(sql.startsWith("select setval("), sql);
        assertTrue(sql.contains("where m.minimo > (select case when is_called then last_value + 1 else last_value end"), sql);
        assertTrue(sql.contains("'^SP-[0-9]{1,15}$'"), sql);

        when(jdbc.queryForList(sql, Long.class)).thenReturn(java.util.Collections.singletonList(1371L));
        assertDoesNotThrow(() -> verificador.alinear());
        verify(jdbc).queryForList(sql, Long.class);
    }

    @Test
    void si_no_se_puede_crear_igual_se_intenta_alinear_y_nada_frena_el_arranque() {
        when(jdbc.queryForObject(SolicitudPagoNumeroVerificador.EXISTE, Boolean.class))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sin permiso"));
        when(jdbc.queryForList(SolicitudPagoNumeroVerificador.ADELANTAR_SI_ATRASADA, Long.class))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sin base"));

        assertDoesNotThrow(() -> verificador.alinear());

        verify(jdbc).queryForList(SolicitudPagoNumeroVerificador.ADELANTAR_SI_ATRASADA, Long.class);
    }
}
