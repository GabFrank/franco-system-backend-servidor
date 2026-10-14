package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EntradaVaria;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.OperacionFinanciera;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.financiero.EntradaVariaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.OperacionFinancieraRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Altas con clave de idempotencia (issue #376): qué operación se registra, que el alta corre una vez y
 * qué se devuelve cuando el pedido es una repetición, entidad por entidad.
 */
class AltaIdempotenteServiceTest {

    private IdempotenciaService idempotencia;
    private EntradaVariaRepository entradaVariaRepository;
    private OperacionFinancieraRepository operacionFinancieraRepository;
    private MovimientoCajaVirtualRepository movimientoRepository;
    private SolicitudPagoRepository solicitudPagoRepository;
    private ValeRepository valeRepository;
    private PrestamoRepository prestamoRepository;
    private AltaIdempotenteService service;
    private final Usuario usuario = new Usuario();
    private final AtomicInteger altas = new AtomicInteger();

    @BeforeEach
    void setUp() {
        idempotencia = mock(IdempotenciaService.class);
        entradaVariaRepository = mock(EntradaVariaRepository.class);
        operacionFinancieraRepository = mock(OperacionFinancieraRepository.class);
        movimientoRepository = mock(MovimientoCajaVirtualRepository.class);
        solicitudPagoRepository = mock(SolicitudPagoRepository.class);
        valeRepository = mock(ValeRepository.class);
        prestamoRepository = mock(PrestamoRepository.class);
        service = new AltaIdempotenteService(idempotencia, entradaVariaRepository, operacionFinancieraRepository,
                movimientoRepository, solicitudPagoRepository, valeRepository, prestamoRepository);
    }

    /** La clave es nueva: corre el alta. */
    private void pedidoNuevo() {
        when(idempotencia.ejecutar(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> ((Supplier<?>) i.getArgument(4)).get());
    }

    /** La clave ya existe con el resultado {@code id}: no corre el alta, carga lo que creó el original. */
    private void repeticionDe(long id) {
        when(idempotencia.ejecutar(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> ((Function<Long, ?>) i.getArgument(6)).apply(id));
    }

    private <T> Supplier<T> alta(T resultado) {
        return () -> {
            altas.incrementAndGet();
            return resultado;
        };
    }

    private static void seRechazaPorAnulado(Executable llamada) {
        GraphQLException e = assertThrows(GraphQLException.class, llamada);
        assertTrue(e.getMessage().contains("después se anuló"), e.getMessage());
    }

    @Test
    void un_pedido_nuevo_corre_el_alta_con_la_operacion_la_huella_y_el_usuario_que_le_tocan() {
        pedidoNuevo();
        EntradaVaria creada = new EntradaVaria();

        assertSame(creada, service.entradaVaria("clave-1", "huella-1", usuario, alta(creada)));

        assertEquals(1, altas.get());
        verify(idempotencia).ejecutar(eq("clave-1"), eq("ENTRADA_VARIA"), eq("huella-1"), same(usuario), any(), any(), any());
    }

    @Test
    void cada_alta_usa_su_propia_operacion_una_clave_no_vale_para_otra() {
        pedidoNuevo();
        service.operacionFinanciera("c", "h", usuario, alta(new OperacionFinanciera()));
        service.maletin(true, "c", "h", usuario, alta(new MovimientoCajaVirtual()));
        service.maletin(false, "c", "h", usuario, alta(new MovimientoCajaVirtual()));
        service.gastoParaPago("c", "h", usuario, alta(new SolicitudPago()));
        service.valeParaPago("c", "h", usuario, alta(new Vale()));
        service.prestamo("c", "h", usuario, alta(new Prestamo()));

        for (String operacion : new String[]{"OPERACION_FINANCIERA", "MALETIN_INGRESO", "MALETIN_EGRESO",
                "GASTO_PARA_PAGO", "VALE_PARA_PAGO", "PRESTAMO_CON_DESEMBOLSO"}) {
            verify(idempotencia).ejecutar(eq("c"), eq(operacion), eq("h"), same(usuario), any(), any(), any());
        }
        // Caben en la columna (varchar 40).
        assertTrue(AltaIdempotenteService.OPERACION_PRESTAMO.length() <= 40);
    }

    @Test
    void la_repeticion_de_una_entrada_varia_devuelve_la_original_sin_registrar_otra() {
        repeticionDe(7L);
        EntradaVaria original = new EntradaVaria();
        when(entradaVariaRepository.findAnuladoById(7L)).thenReturn(Optional.of(false));
        when(entradaVariaRepository.findById(7L)).thenReturn(Optional.of(original));

        assertSame(original, service.entradaVaria("c", "h", usuario, alta(new EntradaVaria())));
        assertEquals(0, altas.get());
    }

    @Test
    void la_repeticion_de_una_entrada_varia_o_de_una_operacion_anulada_se_rechaza() {
        repeticionDe(7L);
        when(entradaVariaRepository.findAnuladoById(7L)).thenReturn(Optional.of(true));
        when(operacionFinancieraRepository.findAnuladoById(7L)).thenReturn(Optional.of(true));

        seRechazaPorAnulado(() -> service.entradaVaria("c", "h", usuario, alta(new EntradaVaria())));
        seRechazaPorAnulado(() -> service.operacionFinanciera("c", "h", usuario, alta(new OperacionFinanciera())));
        assertEquals(0, altas.get());
    }

    @Test
    void la_repeticion_de_una_operacion_financiera_vigente_la_devuelve() {
        repeticionDe(7L);
        OperacionFinanciera original = new OperacionFinanciera();
        when(operacionFinancieraRepository.findAnuladoById(7L)).thenReturn(Optional.of(false));
        when(operacionFinancieraRepository.findById(7L)).thenReturn(Optional.of(original));

        assertSame(original, service.operacionFinanciera("c", "h", usuario, alta(new OperacionFinanciera())));
    }

    @Test
    void un_movimiento_de_maletin_revertido_esta_inactivo_no_anulado_y_nulo_cuenta_como_activo() {
        repeticionDe(7L);
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        when(movimientoRepository.findById(7L)).thenReturn(Optional.of(mov));

        mov.setActivo(null);
        assertSame(mov, service.maletin(false, "c", "h", usuario, alta(new MovimientoCajaVirtual())));
        mov.setActivo(true);
        assertSame(mov, service.maletin(true, "c", "h", usuario, alta(new MovimientoCajaVirtual())));
        mov.setActivo(false);
        seRechazaPorAnulado(() -> service.maletin(false, "c", "h", usuario, alta(new MovimientoCajaVirtual())));
    }

    @Test
    void un_gasto_ya_pagado_se_devuelve_y_uno_cancelado_se_rechaza() {
        repeticionDe(7L);
        SolicitudPago gasto = new SolicitudPago();
        when(solicitudPagoRepository.findById(7L)).thenReturn(Optional.of(gasto));

        gasto.setEstado(SolicitudPagoEstado.PARCIAL);
        assertSame(gasto, service.gastoParaPago("c", "h", usuario, alta(new SolicitudPago())));
        gasto.setEstado(SolicitudPagoEstado.CANCELADO);
        seRechazaPorAnulado(() -> service.gastoParaPago("c", "h", usuario, alta(new SolicitudPago())));
    }

    @Test
    void un_vale_ya_pagado_se_devuelve_y_uno_anulado_se_rechaza() {
        repeticionDe(7L);
        Vale vale = new Vale();
        when(valeRepository.findById(7L)).thenReturn(Optional.of(vale));

        vale.setEstado(ValeEstado.CONFIRMADO);
        assertSame(vale, service.valeParaPago("c", "h", usuario, alta(new Vale())));
        vale.setEstado(ValeEstado.ANULADO);
        seRechazaPorAnulado(() -> service.valeParaPago("c", "h", usuario, alta(new Vale())));
    }

    @Test
    void un_prestamo_ya_pagado_se_devuelve_y_uno_cancelado_se_rechaza() {
        repeticionDe(7L);
        Prestamo prestamo = new Prestamo();
        when(prestamoRepository.findById(7L)).thenReturn(Optional.of(prestamo));

        prestamo.setEstado(PrestamoEstado.PAGADO);
        assertSame(prestamo, service.prestamo("c", "h", usuario, alta(new Prestamo())));
        prestamo.setEstado(PrestamoEstado.CANCELADO);
        seRechazaPorAnulado(() -> service.prestamo("c", "h", usuario, alta(new Prestamo())));
        assertEquals(0, altas.get());
    }

    @Test
    void si_lo_que_creo_el_original_ya_no_existe_no_se_inventa_un_resultado() {
        repeticionDe(7L);
        when(valeRepository.findById(7L)).thenReturn(Optional.empty());

        // Nulo: IdempotenciaService lo convierte en «quedó registrado sin resultado».
        assertNull(service.valeParaPago("c", "h", usuario, alta(new Vale())));
    }

    @Test
    void un_monto_que_no_es_un_numero_se_rechaza_antes_de_la_huella() {
        assertNull(AltaIdempotenteService.monto(null));
        assertEquals(0, AltaIdempotenteService.monto(10.50).compareTo(new java.math.BigDecimal("10.5")));
        assertThrows(GraphQLException.class, () -> AltaIdempotenteService.monto(Double.NaN));
        assertThrows(GraphQLException.class, () -> AltaIdempotenteService.monto(Double.POSITIVE_INFINITY));
    }
}
