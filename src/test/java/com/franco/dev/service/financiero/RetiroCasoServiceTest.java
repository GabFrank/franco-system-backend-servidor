package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.RetiroCaso;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.RetiroVerificacionDetalle;
import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import com.franco.dev.domain.financiero.enums.VeredictoCasoRetiro;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tomar, soltar y resolver un caso de retiro: con lock, con el estado leido de la base, y con la
 * anulacion de la verificacion dentro de la misma operacion (issue #376). El rollback real lo prueba
 * RetiroCasoIT; aca, el orden y las reglas.
 */
class RetiroCasoServiceTest {

    private RetiroCasoRepository casoRepository;
    private RetiroRepository retiroRepository;
    private RetiroVerificacionService verificacionService;
    private UsuarioService usuarioService;
    private EntityManager entityManager;
    private RetiroCasoService service;

    private RetiroCaso caso;
    private Usuario ana;     // la investigadora
    private Usuario beto;    // otro con el rol
    private Usuario contador; // quien hizo la verificacion

    @BeforeEach
    void setUp() {
        casoRepository = mock(RetiroCasoRepository.class);
        retiroRepository = mock(RetiroRepository.class);
        verificacionService = mock(RetiroVerificacionService.class);
        usuarioService = mock(UsuarioService.class);
        PersonaService personaService = mock(PersonaService.class);
        entityManager = mock(EntityManager.class);
        service = new RetiroCasoService(casoRepository, retiroRepository, verificacionService,
                usuarioService, personaService, entityManager);

        ana = usuario(1L, "ANA");
        beto = usuario(2L, "BETO");
        contador = usuario(3L, "CONTADOR");
        when(usuarioService.findById(1L)).thenReturn(Optional.of(ana));
        when(usuarioService.findById(2L)).thenReturn(Optional.of(beto));
        when(usuarioService.findById(3L)).thenReturn(Optional.of(contador));
        when(personaService.findById(anyLong())).thenReturn(Optional.of(new Persona()));

        RetiroVerificacion verificacion = new RetiroVerificacion();
        verificacion.setId(30L);
        verificacion.setUsuario(contador);
        RetiroVerificacionDetalle falta = new RetiroVerificacionDetalle();
        falta.setDiferencia(new BigDecimal("-1000"));
        verificacion.setDetalles(Collections.singletonList(falta));

        caso = new RetiroCaso();
        caso.setId(10L);
        caso.setRetiroId(700L);
        caso.setSucursalId(5L);
        caso.setVerificacion(verificacion);
        caso.setEstado(EstadoCasoRetiro.EN_INVESTIGACION);
        caso.setAsignadoA(ana);
        when(casoRepository.findById(10L)).thenReturn(Optional.of(caso));
        when(casoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Usuario usuario(long id, String nick) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNickname(nick);
        return u;
    }

    /** Lo que otra transaccion dejo commiteado mientras esta esperaba el lock: lo trae el refresh. */
    private void alRefrescar(Consumer<RetiroCaso> cambio) {
        doAnswer(inv -> { cambio.accept(inv.getArgument(0)); return null; })
                .when(entityManager).refresh(any(RetiroCaso.class), eq(LockModeType.PESSIMISTIC_WRITE));
    }

    private RetiroCaso resolver(VeredictoCasoRetiro veredicto, Boolean anular, Usuario actual, boolean superusuario) {
        return service.resolver(10L, veredicto, "informe", 99L, null, anular, actual, superusuario);
    }

    // ── resolver ──

    @Test
    void resolver_sin_anular_toma_solo_el_caso_y_lo_deja_resuelto() {
        RetiroCaso r = resolver(VeredictoCasoRetiro.FALTANTE_PDV, false, ana, false);

        verify(entityManager).refresh(caso, LockModeType.PESSIMISTIC_WRITE);
        verify(retiroRepository, never()).lockByIdAndSucursalId(anyLong(), anyLong());
        verify(verificacionService, never()).anular(anyLong(), any(), any());
        assertEquals(EstadoCasoRetiro.RESUELTO, r.getEstado());
        assertEquals(VeredictoCasoRetiro.FALTANTE_PDV, r.getVeredicto());
        assertEquals("INFORME", r.getResolucion());
        assertSame(ana, r.getResueltoPor());
    }

    @Test
    void resolver_anulando_toma_el_retiro_antes_que_el_caso_y_guarda_antes_de_anular() {
        resolver(VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA, true, ana, false);

        InOrder orden = inOrder(retiroRepository, entityManager, casoRepository, verificacionService);
        orden.verify(retiroRepository).lockByIdAndSucursalId(700L, 5L);
        orden.verify(entityManager).refresh(caso, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(casoRepository).save(caso);
        orden.verify(verificacionService).anular(eq(30L), eq("ERROR DE CONTEO - CASO 10"), eq(ana));
    }

    @Test
    void si_la_anulacion_se_rechaza_el_rechazo_sale_del_metodo() {
        // Es lo que hace que la transaccion se deshaga entera, caso incluido (el rollback, en RetiroCasoIT).
        when(verificacionService.anular(anyLong(), any(), any()))
                .thenThrow(new GraphQLException("Saldo insuficiente en la caja virtual"));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA, true, ana, false));

        assertTrue(e.getMessage().contains("Saldo insuficiente"), e.getMessage());
    }

    @Test
    void resolver_mira_el_estado_de_la_base_y_no_el_de_la_instancia_ya_cargada() {
        alRefrescar(c -> c.setEstado(EstadoCasoRetiro.RESUELTO));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(VeredictoCasoRetiro.FALTANTE_PDV, false, ana, false));

        assertEquals("El caso ya está resuelto", e.getMessage());
        verify(casoRepository, never()).save(any());
    }

    @Test
    void resolver_mira_a_quien_esta_asignado_el_caso_en_la_base() {
        // Mientras esperaba el lock, el caso paso a manos de Beto.
        alRefrescar(c -> c.setAsignadoA(beto));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(VeredictoCasoRetiro.FALTANTE_PDV, false, ana, false));

        assertTrue(e.getMessage().startsWith("El caso lo está investigando BETO"), e.getMessage());
        verify(casoRepository, never()).save(any());
    }

    @Test
    void resolver_un_caso_ajeno_solo_lo_puede_el_superusuario() {
        assertThrows(GraphQLException.class, () -> resolver(VeredictoCasoRetiro.FALTANTE_PDV, false, beto, false));

        RetiroCaso r = resolver(VeredictoCasoRetiro.FALTANTE_PDV, false, beto, true);
        assertEquals(EstadoCasoRetiro.RESUELTO, r.getEstado());
    }

    @Test
    void resolver_un_caso_sin_tomar_pide_tomarlo() {
        caso.setAsignadoA(null);
        caso.setEstado(EstadoCasoRetiro.ABIERTO);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(VeredictoCasoRetiro.FALTANTE_PDV, false, ana, false));

        assertEquals("Tomá el caso antes de resolverlo", e.getMessage());
    }

    @Test
    void pedir_anular_un_caso_sin_verificacion_se_rechaza_en_vez_de_resolverlo_sin_anular_nada() {
        caso.setVerificacion(null);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(VeredictoCasoRetiro.ERROR_DE_CONTEO_TESORERIA, true, ana, false));

        assertTrue(e.getMessage().contains("no tiene una verificación para anular"), e.getMessage());
        verify(casoRepository, never()).save(any());
        verify(verificacionService, never()).anular(anyLong(), any(), any());
    }

    @Test
    void las_validaciones_de_siempre_conservan_su_mensaje() {
        assertEquals("Falta el veredicto: sin él el caso no se puede clasificar",
                assertThrows(GraphQLException.class, () -> resolver(null, false, ana, false)).getMessage());
        assertEquals("Este veredicto necesita un responsable identificado",
                assertThrows(GraphQLException.class, () -> service.resolver(10L, VeredictoCasoRetiro.FALTANTE_PDV,
                        "x", null, null, false, ana, false)).getMessage());
        assertEquals("Indicá el retiro por el que se repuso la diferencia",
                assertThrows(GraphQLException.class, () -> service.resolver(10L, VeredictoCasoRetiro.REINTEGRADO,
                        "x", null, null, false, ana, false)).getMessage());
        assertEquals("El conteo dice que vino de menos, no de más. Si la plata quedó en la caja del cajero, igual al sobre le faltó.",
                assertThrows(GraphQLException.class, () -> resolver(VeredictoCasoRetiro.SOBRANTE_PDV, false, ana, false)).getMessage());
        assertEquals("Solo se anula la verificación cuando el error fue del conteo de tesorería",
                assertThrows(GraphQLException.class, () -> resolver(VeredictoCasoRetiro.FALTANTE_PDV, true, ana, false)).getMessage());
        verify(casoRepository, never()).save(any());
    }

    // ── asignar ──

    @Test
    void asignar_un_caso_abierto_lo_pasa_a_investigacion() {
        caso.setAsignadoA(null);
        caso.setEstado(EstadoCasoRetiro.ABIERTO);

        RetiroCaso r = service.asignar(10L, 2L, false);

        verify(entityManager).refresh(caso, LockModeType.PESSIMISTIC_WRITE);
        assertSame(beto, r.getAsignadoA());
        assertEquals(EstadoCasoRetiro.EN_INVESTIGACION, r.getEstado());
    }

    @Test
    void asignar_no_reabre_un_caso_resuelto() {
        caso.setAsignadoA(null);
        caso.setEstado(EstadoCasoRetiro.ABIERTO);
        alRefrescar(c -> { c.setEstado(EstadoCasoRetiro.RESUELTO); c.setVeredicto(VeredictoCasoRetiro.FALTANTE_PDV); });

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.asignar(10L, 2L, false));

        assertEquals("El caso ya está resuelto", e.getMessage());
        assertEquals(EstadoCasoRetiro.RESUELTO, caso.getEstado());
        verify(casoRepository, never()).save(any());
    }

    @Test
    void asignar_no_le_saca_el_caso_a_quien_lo_esta_investigando() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.asignar(10L, 2L, false));

        assertEquals("El caso ya lo tomó ANA.", e.getMessage());
        assertSame(ana, caso.getAsignadoA());
        verify(casoRepository, never()).save(any());
    }

    @Test
    void el_superusuario_si_puede_reasignar_un_caso_tomado() {
        RetiroCaso r = service.asignar(10L, 2L, true);

        assertSame(beto, r.getAsignadoA());
        verify(casoRepository).save(caso);
    }

    @Test
    void volver_a_tomar_el_propio_caso_no_falla_ni_escribe() {
        RetiroCaso r = service.asignar(10L, 1L, false);

        assertSame(ana, r.getAsignadoA());
        verify(casoRepository, never()).save(any());
    }

    @Test
    void no_se_asigna_a_quien_hizo_la_verificacion() {
        caso.setAsignadoA(null);
        caso.setEstado(EstadoCasoRetiro.ABIERTO);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.asignar(10L, 3L, false));

        assertEquals("El caso no puede asignarse a quien hizo la verificación", e.getMessage());
    }

    // ── soltar ──

    @Test
    void soltar_devuelve_el_caso_a_abierto_aunque_lo_tenga_otro() {
        RetiroCaso r = service.soltar(10L);

        verify(entityManager).refresh(caso, LockModeType.PESSIMISTIC_WRITE);
        assertEquals(EstadoCasoRetiro.ABIERTO, r.getEstado());
        assertNull(r.getAsignadoA());
    }

    @Test
    void soltar_no_reabre_un_caso_que_se_resolvio_mientras_esperaba_el_lock() {
        alRefrescar(c -> { c.setEstado(EstadoCasoRetiro.RESUELTO); c.setVeredicto(VeredictoCasoRetiro.FALTANTE_PDV); });

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.soltar(10L));

        assertEquals("El caso ya está resuelto", e.getMessage());
        assertEquals(VeredictoCasoRetiro.FALTANTE_PDV, caso.getVeredicto());
        verify(casoRepository, never()).save(any());
    }

    @Test
    void un_caso_inexistente_lo_dice() {
        when(casoRepository.findById(404L)).thenReturn(Optional.empty());
        assertEquals("Caso no encontrado: 404",
                assertThrows(GraphQLException.class, () -> service.soltar(404L)).getMessage());
    }

    @Test
    void tomar_un_caso_que_figura_abierto_a_nombre_propio_lo_deja_en_investigacion() {
        caso.setEstado(EstadoCasoRetiro.ABIERTO);   // dato incoherente: abierto pero con dueno

        RetiroCaso r = service.asignar(10L, 1L, false);

        assertEquals(EstadoCasoRetiro.EN_INVESTIGACION, r.getEstado());
        verify(casoRepository).save(caso);
    }

    @Test
    void las_tres_operaciones_corren_en_una_transaccion() {
        // Sin ella el refresh con lock no tiene donde vivir y resolver no deshace el caso si la anulacion falla.
        for (String nombre : new String[]{"asignar", "soltar", "resolver"}) {
            assertTrue(java.util.Arrays.stream(RetiroCasoService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(nombre))
                    .allMatch(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)),
                    nombre + " no es @Transactional");
        }
    }
}
