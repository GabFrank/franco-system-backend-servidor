package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import com.franco.dev.domain.rrhh.FuncionarioEgresoHistorico;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.repository.rrhh.FuncionarioEgresoHistoricoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reversa de un egreso hecho por error.
 *
 * <p>Usa el {@link FuncionarioService} REAL, no un mock, porque lo que hay que probar es
 * justamente lo que ese servicio hace de mas al guardar: pone el credito en cero cuando
 * activo llega en false, e inactiva usuario y cliente por cascada. Con un mock, el test
 * pasaria sin demostrar nada.</p>
 */
class RevertirEgresoFuncionarioTest {

    private static final Long FUNC_ID = 1L;
    private static final Long PERSONA_ID = 7L;
    private static final float CREDITO_PREVIO = 400000f;

    private FuncionarioRepository funcionarioRepository;
    private UsuarioService usuarioService;
    private ClienteService clienteService;
    private LiquidacionFinalRepository liquidacionFinalRepository;
    private FuncionarioEgresoHistoricoRepository egresoHistoricoRepository;
    private FuncionarioService funcionarioService;
    private FuncionarioRrhhService service;

    private Persona persona;
    private Usuario usuario;
    private Cliente cliente;
    private Funcionario funcionario;

    @BeforeEach
    void setUp() {
        funcionarioRepository = mock(FuncionarioRepository.class);
        usuarioService = mock(UsuarioService.class);
        clienteService = mock(ClienteService.class);
        liquidacionFinalRepository = mock(LiquidacionFinalRepository.class);
        egresoHistoricoRepository = mock(FuncionarioEgresoHistoricoRepository.class);

        funcionarioService = new FuncionarioService(funcionarioRepository, usuarioService, clienteService);

        service = new FuncionarioRrhhService(
                funcionarioService,
                mock(CargoService.class),
                mock(MonedaService.class),
                usuarioService,
                mock(FuncionarioCargoHistoricoService.class),
                mock(FuncionarioSalarioHistoricoService.class),
                clienteService,
                liquidacionFinalRepository,
                egresoHistoricoRepository);

        persona = new Persona();
        persona.setId(PERSONA_ID);

        // Estado en el que queda todo DESPUES de un egreso: apagado y con credito en cero.
        usuario = new Usuario();
        usuario.setId(70L);
        usuario.setActivo(false);

        cliente = new Cliente();
        cliente.setId(700L);
        cliente.setActivo(false);
        cliente.setTipo(TipoCliente.NORMAL);
        cliente.setCredito(0f);

        funcionario = new Funcionario();
        funcionario.setId(FUNC_ID);
        funcionario.setPersona(persona);
        funcionario.setActivo(false);
        funcionario.setCredito(0f);
        funcionario.setFechaEgreso(LocalDateTime.of(2026, 8, 21, 0, 0));
        funcionario.setMotivoEgreso("ERROR DE CARGA");

        when(usuarioService.findByPersonaId(PERSONA_ID)).thenReturn(usuario);
        when(clienteService.findByPersonaId(PERSONA_ID)).thenReturn(cliente);
        when(funcionarioRepository.findById(FUNC_ID)).thenReturn(Optional.of(funcionario));
        when(funcionarioRepository.findActivoById(FUNC_ID)).thenReturn(false);
        when(funcionarioRepository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));
        when(liquidacionFinalRepository.findByFuncionarioIdOrderByCreadoEnDesc(FUNC_ID))
                .thenReturn(Collections.emptyList());
        // Por defecto: egreso viejo, sin snapshot. Los tests que lo necesitan lo cargan.
        when(egresoHistoricoRepository.findFirstByFuncionarioIdAndRevertidoEnIsNullOrderByIdDesc(FUNC_ID))
                .thenReturn(Optional.empty());
        when(egresoHistoricoRepository.save(any(FuncionarioEgresoHistorico.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    /** Deja disponible un snapshot del egreso, como si lo hubiera dejado egresar(). */
    private FuncionarioEgresoHistorico conSnapshot(String creditoFunc, String creditoCli, TipoCliente tipoPrevio) {
        FuncionarioEgresoHistorico snap = new FuncionarioEgresoHistorico();
        snap.setId(9L);
        snap.setFuncionario(funcionario);
        snap.setCreditoAnterior(new java.math.BigDecimal(creditoFunc));
        snap.setClienteCreditoAnterior(new java.math.BigDecimal(creditoCli));
        snap.setClienteTipoAnterior(tipoPrevio != null ? tipoPrevio.name() : null);
        when(egresoHistoricoRepository.findFirstByFuncionarioIdAndRevertidoEnIsNullOrderByIdDesc(FUNC_ID))
                .thenReturn(Optional.of(snap));
        return snap;
    }

    @Test
    void dejaAlFuncionarioActivoYSinRastroDeEgreso() {
        Funcionario r = service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, "egreso por error");
        assertTrue(Boolean.TRUE.equals(r.getActivo()), "el funcionario tiene que quedar activo");
        assertNull(r.getFechaEgreso(), "la fecha de egreso tiene que quedar limpia");
        assertNull(r.getMotivoEgreso(), "el motivo de egreso tiene que quedar limpio");
    }

    /**
     * El punto del parametro credito. Ojo con el orden: save() pisa el credito a cero
     * cuando activo viene en false, asi que si activo se seteara despues, esto daria 0.
     */
    @Test
    void restauraElCreditoDelFuncionario() {
        Funcionario r = service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, null);
        assertEquals(CREDITO_PREVIO, r.getCredito(), 0.001f,
                "el credito volvio en " + r.getCredito() + " en vez del previo al egreso");
    }

    /** Reactivar al funcionario tiene que devolverle el login. */
    @Test
    void reactivaElUsuario() {
        service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, null);
        assertTrue(Boolean.TRUE.equals(usuario.getActivo()), "el usuario quedo sin poder entrar");
    }

    /**
     * La cascada reactiva al cliente y lo devuelve a FUNCIONARIO, pero le deja el credito
     * en cero: esa re-sincronizacion vive en el resolver de saveFuncionario, no en el
     * servicio. Sin hacerla a mano, el funcionario queda con credito y el cliente sin.
     */
    @Test
    void reactivaAlClienteConSuCreditoYSuTipo() {
        service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, null);
        assertTrue(Boolean.TRUE.equals(cliente.getActivo()), "el cliente quedo inactivo");
        assertEquals(TipoCliente.FUNCIONARIO, cliente.getTipo(), "el cliente no volvio a FUNCIONARIO");
        assertEquals(CREDITO_PREVIO, cliente.getCredito(), 0.001f,
                "el cliente quedo con credito " + cliente.getCredito() + ": la re-sincronizacion no corrio");
    }

    /** Sin credito indicado no se inventa uno: queda en cero, que es como lo dejo el egreso. */
    @Test
    void sinCreditoIndicadoQuedaEnCero() {
        Funcionario r = service.revertirEgreso(FUNC_ID, null, null);
        assertEquals(0f, r.getCredito(), 0.001f);
        assertEquals(0f, cliente.getCredito(), 0.001f);
    }

    @Test
    void rechazaSiYaEstaActivo() {
        funcionario.setActivo(true);
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, null));
        assertTrue(e.getMessage().toLowerCase().contains("ya esta activo"), e.getMessage());
    }

    /**
     * Un finiquito PAGADA significa que ya salio plata de la caja por esa salida. Revertir
     * el egreso dejandolo en pie deja al funcionario activo y cobrado como si se hubiera ido.
     */
    @Test
    void rechazaSiHayFiniquitoVigente() {
        LiquidacionFinal lf = new LiquidacionFinal();
        lf.setId(55L);
        lf.setEstado(LiquidacionFinalEstado.PAGADA);
        when(liquidacionFinalRepository.findByFuncionarioIdOrderByCreadoEnDesc(FUNC_ID))
                .thenReturn(Collections.singletonList(lf));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, null));
        assertTrue(e.getMessage().contains("PAGADA") && e.getMessage().contains("55"), e.getMessage());
    }

    /** Un finiquito ya anulado no bloquea: es justamente el camino para poder revertir. */
    @Test
    void unFiniquitoAnuladoNoBloquea() {
        LiquidacionFinal anulada = new LiquidacionFinal();
        anulada.setId(56L);
        anulada.setEstado(LiquidacionFinalEstado.ANULADA);
        when(liquidacionFinalRepository.findByFuncionarioIdOrderByCreadoEnDesc(FUNC_ID))
                .thenReturn(Arrays.asList(anulada));

        Funcionario r = service.revertirEgreso(FUNC_ID, CREDITO_PREVIO, null);
        assertTrue(Boolean.TRUE.equals(r.getActivo()));
    }

    @Test
    void rechazaSiElFuncionarioNoExiste() {
        when(funcionarioRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> service.revertirEgreso(99L, CREDITO_PREVIO, null));
    }

    // --- Con snapshot: la reversa restaura en vez de preguntar -------------------------

    /** Sin indicar credito, el monto sale del snapshot que dejo el egreso. */
    @Test
    void conSnapshotElCreditoSaleDeLaFotoYNoHaceFaltaEscribirlo() {
        conSnapshot("750000", "750000", TipoCliente.FUNCIONARIO);
        Funcionario r = service.revertirEgreso(FUNC_ID, null, "error de carga");
        assertEquals(750000f, r.getCredito(), 0.001f,
                "el credito tenia que salir del snapshot, salio " + r.getCredito());
        assertEquals(750000f, cliente.getCredito(), 0.001f);
    }

    /**
     * El caso que motivo guardar el tipo: la cascada reactiva al cliente como FUNCIONARIO
     * siempre. Si antes era VIP y no se restaura, la reversa le saca la categoria sin que
     * nadie lo haya pedido.
     */
    @Test
    void unClienteVipRecuperaSuCategoria() {
        cliente.setTipo(TipoCliente.NORMAL);
        conSnapshot("900000", "900000", TipoCliente.VIP);
        service.revertirEgreso(FUNC_ID, null, null);
        assertEquals(TipoCliente.VIP, cliente.getTipo(),
                "el cliente quedo como " + cliente.getTipo() + ": la reversa lo degrado");
    }

    /** Lo que el usuario escribe gana sobre la foto: entre egreso y reversa todo pudo cambiar. */
    @Test
    void elOverrideManualGanaSobreElSnapshot() {
        conSnapshot("750000", "750000", TipoCliente.FUNCIONARIO);
        Funcionario r = service.revertirEgreso(FUNC_ID, 123000f, null);
        assertEquals(123000f, r.getCredito(), 0.001f);
        assertEquals(123000f, cliente.getCredito(), 0.001f);
    }

    /** Revertir marca la foto como usada, con quien y por que. */
    @Test
    void laReversaMarcaElSnapshotComoRevertido() {
        FuncionarioEgresoHistorico snap = conSnapshot("500000", "500000", TipoCliente.FUNCIONARIO);
        service.revertirEgreso(FUNC_ID, null, "egreso por error");
        assertNotNull(snap.getRevertidoEn(), "el snapshot quedo sin marcar como revertido");
        assertEquals("egreso por error", snap.getMotivoReversion());
    }

    /**
     * Un tipo que ya no exista en el enum no puede voltear la reversa: se deja lo que puso
     * la cascada y se sigue.
     */
    @Test
    void unTipoDesconocidoEnElSnapshotNoRompe() {
        FuncionarioEgresoHistorico snap = conSnapshot("500000", "500000", null);
        snap.setClienteTipoAnterior("CATEGORIA_QUE_YA_NO_EXISTE");
        Funcionario r = service.revertirEgreso(FUNC_ID, null, null);
        assertTrue(Boolean.TRUE.equals(r.getActivo()));
        assertEquals(TipoCliente.FUNCIONARIO, cliente.getTipo());
    }
}
