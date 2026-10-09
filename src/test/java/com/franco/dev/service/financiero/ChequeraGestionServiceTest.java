package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.repository.financiero.ChequeraRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Alta y edición de chequeras (issue #376): la edición se decide sobre lo que hay en la base, el
 * correlativo solo va hacia adelante, una anulada no se reactiva y los rangos de una cuenta no se pisan.
 */
class ChequeraGestionServiceTest {

    private ChequeraRepository chequeraRepository;
    private ChequeRepository chequeRepository;
    private CuentaBancariaRepository cuentaRepository;
    private BloqueoTransaccionalService bloqueo;
    private EntityManager em;
    private ChequeraGestionService service;

    private CuentaBancaria cuenta;
    private Usuario creador;
    /** La chequera 1 tal como está en la base: rango 100–150, siguiente 112, activa, cuenta 4. */
    private Chequera enLaBase;

    @BeforeEach
    void setUp() {
        chequeraRepository = mock(ChequeraRepository.class);
        chequeRepository = mock(ChequeRepository.class);
        cuentaRepository = mock(CuentaBancariaRepository.class);
        bloqueo = mock(BloqueoTransaccionalService.class);
        em = mock(EntityManager.class);
        service = new ChequeraGestionService(chequeraRepository, chequeRepository, cuentaRepository, bloqueo);
        service.setEntityManager(em);

        cuenta = new CuentaBancaria();
        cuenta.setId(4L);
        when(cuentaRepository.findById(4L)).thenReturn(Optional.of(cuenta));
        CuentaBancaria otra = new CuentaBancaria();
        otra.setId(9L);
        when(cuentaRepository.findById(9L)).thenReturn(Optional.of(otra));

        creador = new Usuario();
        creador.setId(77L);
        enLaBase = new Chequera();
        enLaBase.setId(1L);
        enLaBase.setCuentaBancaria(cuenta);
        enLaBase.setNombre("CHEQUERA A");
        enLaBase.setRangoDesde(100.0);
        enLaBase.setRangoHasta(150.0);
        enLaBase.setSiguienteNumero(112L);
        enLaBase.setEstado(EstadoChequera.ACTIVA);
        enLaBase.setFechaRetiro(LocalDateTime.of(2026, 9, 1, 10, 0));
        enLaBase.setCreadoEn(LocalDateTime.of(2026, 8, 30, 9, 0));
        enLaBase.setUsuario(creador);
        when(chequeraRepository.findCuentaIdById(1L)).thenReturn(Optional.of(4L));
        when(chequeraRepository.lockById(1L)).thenReturn(Optional.of(enLaBase));
        when(chequeraRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chequeraRepository.findSuperpuestas(anyLong(), any(), any(), any())).thenReturn(Collections.emptyList());
        // Emitió del 100 al 111.
        when(chequeRepository.minNumeroPorChequera(1L)).thenReturn(100.0);
        when(chequeRepository.maxNumeroPorChequera(1L)).thenReturn(111.0);
    }

    /** Lo que manda el desktop al editar: la fila entera, como la tenía en pantalla. */
    private ChequeraInput pantalla(long siguiente, EstadoChequera estado) {
        ChequeraInput in = new ChequeraInput();
        in.setId(1L);
        in.setCuentaBancariaId(4L);
        in.setNombre("CHEQUERA A");
        in.setRangoDesde(100.0);
        in.setRangoHasta(150.0);
        in.setSiguienteNumero(siguiente);
        in.setEstado(estado);
        in.setUsuarioId(5L);
        return in;
    }

    private ChequeraInput alta(double desde, double hasta) {
        ChequeraInput in = new ChequeraInput();
        in.setCuentaBancariaId(4L);
        in.setNombre("NUEVA");
        in.setRangoDesde(desde);
        in.setRangoHasta(hasta);
        return in;
    }

    private void rechaza(ChequeraInput in, String texto) {
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.guardar(in, creador));
        assertTrue(e.getMessage().contains(texto), e.getMessage());
        verify(chequeraRepository, never()).save(any());
    }

    // ── Edición ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void una_pantalla_abierta_antes_de_emitir_no_hace_retroceder_el_correlativo() {
        // La pantalla se cargó con siguiente = 105; después se emitieron cheques y la base va por el 112.
        ChequeraInput vieja = pantalla(105, EstadoChequera.ACTIVA);
        vieja.setNombre("CHEQUERA RENOMBRADA");

        Chequera r = service.guardar(vieja, null);

        assertEquals(112L, r.getSiguienteNumero());
        assertEquals("CHEQUERA RENOMBRADA", r.getNombre());
    }

    @Test
    void una_pantalla_vieja_tampoco_deshace_el_salto_que_hizo_otro_sin_emitir() {
        enLaBase.setSiguienteNumero(120L);   // otro saltó del 112 al 120 por cheques inutilizados

        assertEquals(120L, service.guardar(pantalla(112, EstadoChequera.ACTIVA), null).getSiguienteNumero());
    }

    @Test
    void el_correlativo_se_puede_llevar_hacia_adelante() {
        assertEquals(118L, service.guardar(pantalla(118, EstadoChequera.ACTIVA), null).getSiguienteNumero());
    }

    @Test
    void desactivar_con_la_fila_vieja_anula_sin_tocar_el_correlativo() {
        Chequera r = service.guardar(pantalla(105, EstadoChequera.ANULADA), null);

        assertEquals(EstadoChequera.ANULADA, r.getEstado());
        assertEquals(112L, r.getSiguienteNumero());
    }

    @Test
    void la_edicion_toma_el_lock_y_relee_la_base_antes_de_decidir() {
        // Mientras esperaba el lock se emitió otro cheque: la instancia cargada decía 112, la base ya 113.
        doAnswer(i -> { ((Chequera) i.getArgument(0)).setSiguienteNumero(113L); return null; }).when(em).refresh(enLaBase);

        Chequera r = service.guardar(pantalla(112, EstadoChequera.ACTIVA), null);

        assertEquals(113L, r.getSiguienteNumero());
        InOrder orden = inOrder(bloqueo, chequeraRepository, em);
        orden.verify(bloqueo).tomar("CHEQUERAS_CUENTA:4");
        orden.verify(chequeraRepository).lockById(1L);
        orden.verify(em).refresh(enLaBase);
        orden.verify(chequeraRepository).save(enLaBase);
    }

    @Test
    void la_fecha_de_retiro_la_de_alta_y_el_usuario_creador_se_conservan() {
        Chequera r = service.guardar(pantalla(112, EstadoChequera.ACTIVA), new Usuario());

        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), r.getFechaRetiro());
        assertEquals(LocalDateTime.of(2026, 8, 30, 9, 0), r.getCreadoEn());
        assertSame(creador, r.getUsuario());
    }

    @Test
    void una_pantalla_vieja_no_deja_activa_una_chequera_que_se_agoto() {
        enLaBase.setSiguienteNumero(151L);
        enLaBase.setEstado(EstadoChequera.AGOTADA);
        when(chequeRepository.maxNumeroPorChequera(1L)).thenReturn(150.0);

        assertEquals(EstadoChequera.AGOTADA, service.guardar(pantalla(140, EstadoChequera.ACTIVA), null).getEstado());
    }

    @Test
    void ampliar_el_rango_de_una_agotada_y_pedirla_activa_la_reactiva() {
        enLaBase.setSiguienteNumero(151L);
        enLaBase.setEstado(EstadoChequera.AGOTADA);
        when(chequeRepository.maxNumeroPorChequera(1L)).thenReturn(150.0);
        ChequeraInput in = pantalla(151, EstadoChequera.ACTIVA);
        in.setRangoHasta(200.0);

        Chequera r = service.guardar(in, null);

        assertEquals(EstadoChequera.ACTIVA, r.getEstado());
        assertEquals(200.0, r.getRangoHasta());
    }

    @Test
    void una_chequera_anulada_no_se_reactiva_pero_se_le_puede_corregir_el_nombre() {
        enLaBase.setEstado(EstadoChequera.ANULADA);

        rechaza(pantalla(112, EstadoChequera.ACTIVA), "no se reactiva");

        ChequeraInput soloNombre = pantalla(112, EstadoChequera.ANULADA);
        soloNombre.setNombre("ANULADA POR ERROR");
        soloNombre.setRangoHasta(999.0);   // se ignora: de una anulada solo cambian los textos
        Chequera r = service.guardar(soloNombre, null);
        assertEquals("ANULADA POR ERROR", r.getNombre());
        assertEquals(150.0, r.getRangoHasta());
        assertEquals(EstadoChequera.ANULADA, r.getEstado());
    }

    @Test
    void el_rango_tiene_que_incluir_los_cheques_emitidos_y_al_correlativo() {
        ChequeraInput dejaAfueraEmitidos = pantalla(112, EstadoChequera.ACTIVA);
        dejaAfueraEmitidos.setRangoDesde(105.0);
        rechaza(dejaAfueraEmitidos, "incluir los cheques ya emitidos (100–111)");

        ChequeraInput dejaAfueraAlSiguiente = pantalla(112, EstadoChequera.ACTIVA);
        dejaAfueraAlSiguiente.setRangoHasta(110.0);
        rechaza(dejaAfueraAlSiguiente, "incluir los cheques ya emitidos");

        enLaBase.setSiguienteNumero(130L);   // saltó hasta el 130 sin emitir
        ChequeraInput cortaAntesDelSiguiente = pantalla(130, EstadoChequera.ACTIVA);
        cortaAntesDelSiguiente.setRangoHasta(120.0);
        rechaza(cortaAntesDelSiguiente, "queda fuera del rango 100–120");
    }

    @Test
    void achicar_el_rango_justo_hasta_el_ultimo_emitido_la_deja_agotada() {
        ChequeraInput in = pantalla(112, EstadoChequera.ACTIVA);
        in.setRangoHasta(111.0);

        assertEquals(EstadoChequera.AGOTADA, service.guardar(in, null).getEstado());
    }

    @Test
    void no_se_cambia_la_cuenta_de_una_chequera_que_ya_emitio() {
        ChequeraInput in = pantalla(112, EstadoChequera.ACTIVA);
        in.setCuentaBancariaId(9L);

        rechaza(in, "ya emitió cheques");
    }

    @Test
    void sin_cheques_emitidos_la_cuenta_se_puede_cambiar_tomando_las_dos_cuentas_en_orden() {
        when(chequeRepository.minNumeroPorChequera(1L)).thenReturn(null);
        when(chequeRepository.maxNumeroPorChequera(1L)).thenReturn(null);
        enLaBase.setSiguienteNumero(100L);
        ChequeraInput in = pantalla(100, EstadoChequera.ACTIVA);
        in.setCuentaBancariaId(9L);

        Chequera r = service.guardar(in, null);

        assertEquals(9L, r.getCuentaBancaria().getId());
        InOrder orden = inOrder(bloqueo, chequeraRepository);
        orden.verify(bloqueo).tomar("CHEQUERAS_CUENTA:4");
        orden.verify(bloqueo).tomar("CHEQUERAS_CUENTA:9");
        orden.verify(chequeraRepository).lockById(1L);
        verify(chequeraRepository).findSuperpuestas(9L, 100.0, 150.0, 1L);
    }

    @Test
    void una_edicion_sin_cuenta_conserva_la_de_la_base_y_una_inexistente_se_rechaza() {
        ChequeraInput sinCuenta = pantalla(112, EstadoChequera.ACTIVA);
        sinCuenta.setCuentaBancariaId(null);
        assertSame(cuenta, service.guardar(sinCuenta, null).getCuentaBancaria());

        when(chequeRepository.maxNumeroPorChequera(1L)).thenReturn(null);
        when(chequeRepository.minNumeroPorChequera(1L)).thenReturn(null);
        ChequeraInput inexistente = pantalla(112, EstadoChequera.ACTIVA);
        inexistente.setCuentaBancariaId(404L);
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.guardar(inexistente, null));
        assertTrue(e.getMessage().contains("Cuenta bancaria no encontrada: 404"), e.getMessage());
    }

    @Test
    void si_la_chequera_cambio_de_cuenta_mientras_se_esperaba_se_pide_reintentar() {
        CuentaBancaria otra = new CuentaBancaria();
        otra.setId(9L);
        doAnswer(i -> { ((Chequera) i.getArgument(0)).setCuentaBancaria(otra); return null; }).when(em).refresh(enLaBase);

        rechaza(pantalla(112, EstadoChequera.ACTIVA), "cambió mientras se guardaba");
    }

    // ── Superposición ────────────────────────────────────────────────────────────────────────────

    private Chequera vecina() {
        Chequera v = new Chequera();
        v.setId(2L);
        v.setNombre("CHEQUERA B");
        v.setRangoDesde(140.0);
        v.setRangoHasta(190.0);
        return v;
    }

    @Test
    void un_alta_con_un_rango_que_pisa_el_de_otra_chequera_de_la_cuenta_se_rechaza() {
        when(chequeraRepository.findSuperpuestas(4L, 150.0, 160.0, null)).thenReturn(Collections.singletonList(vecina()));

        rechaza(alta(150, 160), "se superpone con la chequera CHEQUERA B (140–190)");

        InOrder orden = inOrder(bloqueo, chequeraRepository);
        orden.verify(bloqueo).tomar("CHEQUERAS_CUENTA:4");
        orden.verify(chequeraRepository).findSuperpuestas(4L, 150.0, 160.0, null);
    }

    @Test
    void cambiar_el_rango_a_uno_que_pisa_otra_chequera_se_rechaza() {
        when(chequeraRepository.findSuperpuestas(4L, 100.0, 160.0, 1L)).thenReturn(Collections.singletonList(vecina()));
        ChequeraInput in = pantalla(112, EstadoChequera.ACTIVA);
        in.setRangoHasta(160.0);

        rechaza(in, "se superpone");
    }

    @Test
    void editar_sin_tocar_el_rango_no_revisa_la_superposicion_aunque_ya_estuviera_superpuesta() {
        when(chequeraRepository.findSuperpuestas(anyLong(), any(), any(), any())).thenReturn(Collections.singletonList(vecina()));

        service.guardar(pantalla(112, EstadoChequera.ACTIVA), null);

        verify(chequeraRepository, never()).findSuperpuestas(anyLong(), any(), any(), any());
    }

    // ── Alta ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    void un_alta_valida_arranca_en_el_primer_numero_activa_y_a_nombre_de_la_sesion() {
        Chequera r = service.guardar(alta(500, 549), creador);

        assertEquals(500L, r.getSiguienteNumero());
        assertEquals(EstadoChequera.ACTIVA, r.getEstado());
        assertSame(cuenta, r.getCuentaBancaria());
        assertSame(creador, r.getUsuario());
        assertNotNull(r.getCreadoEn());
    }

    @Test
    void altas_invalidas_se_rechazan() {
        ChequeraInput sinCuenta = alta(500, 549);
        sinCuenta.setCuentaBancariaId(null);
        rechaza(sinCuenta, "requiere una cuenta bancaria");

        ChequeraInput cuentaInexistente = alta(500, 549);
        cuentaInexistente.setCuentaBancariaId(404L);
        rechaza(cuentaInexistente, "Cuenta bancaria no encontrada: 404");

        rechaza(alta(549, 500), "no puede ser menor");
        rechaza(alta(0, 10), "mayor a cero");
        rechaza(alta(500.5, 549), "número entero");
        ChequeraInput sinRango = alta(500, 549);
        sinRango.setRangoHasta(null);
        rechaza(sinRango, "Falta el rango");

        ChequeraInput siguienteAfuera = alta(500, 549);
        siguienteAfuera.setSiguienteNumero(600L);
        rechaza(siguienteAfuera, "dentro del rango 500–549");
    }

    @Test
    void sin_cheques_emitidos_y_sin_correlativo_en_la_base_arranca_en_el_primer_numero_del_rango() {
        when(chequeRepository.minNumeroPorChequera(1L)).thenReturn(null);
        when(chequeRepository.maxNumeroPorChequera(1L)).thenReturn(null);
        enLaBase.setSiguienteNumero(null);
        ChequeraInput in = pantalla(100, EstadoChequera.ACTIVA);
        in.setSiguienteNumero(null);

        assertEquals(100L, service.guardar(in, null).getSiguienteNumero());
    }
}
