package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroSituacion;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Cancelar / habilitar un retiro (issue #376): el pedido dice cómo tiene que quedar, así que repetirlo
 * no lo invierte, y un retiro que ya entró a la caja mayor no se cancela.
 */
class RetiroServiceTest {

    private RetiroRepository repository;
    private ApplicationEventPublisher publisher;
    private RetiroVerificacionRepository verificacionRepository;
    private TesoreriaSecurityService seguridad;
    private com.franco.dev.repository.financiero.RetiroDetalleRepository detalleRepository;
    private RetiroService service;

    /** Lo que hay en la base: el mock la mantiene para que un segundo pedido vea lo que dejó el primero. */
    private EstadoRetiro estado;
    private Long movimientoId;
    private Long cajaMayorId;

    @BeforeEach
    void setUp() {
        repository = mock(RetiroRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        verificacionRepository = mock(RetiroVerificacionRepository.class);
        seguridad = mock(TesoreriaSecurityService.class);
        detalleRepository = mock(com.franco.dev.repository.financiero.RetiroDetalleRepository.class);
        service = new RetiroService(repository, publisher, verificacionRepository, seguridad, null, null,
                detalleRepository);

        // La instancia que devuelve el lock queda a propósito con el estado viejo (nulo): si el
        // servicio decidiera con ella, los tests de repetición fallarían.
        when(repository.lockByIdAndSucursalId(7L, 1L)).thenReturn(Optional.of(new Retiro()));
        when(repository.findSituacion(7L, 1L))
                .thenAnswer(i -> Optional.of(new RetiroSituacion(estado, movimientoId, cajaMayorId)));
        when(repository.marcarCancelado(7L, 1L)).thenAnswer(i -> { estado = EstadoRetiro.CANCELADO; return 1; });
        when(repository.marcarConcluido(7L, 1L)).thenAnswer(i -> { estado = EstadoRetiro.CONCLUIDO; return 1; });
        when(verificacionRepository.findVigente(7L, 1L)).thenReturn(Optional.empty());
    }

    private void assertNoSeEscribio() {
        verify(repository, never()).marcarCancelado(anyLong(), anyLong());
        verify(repository, never()).marcarConcluido(anyLong(), anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void cancelar_dos_veces_lo_deja_cancelado_y_escribe_una_sola_vez() {
        assertTrue(service.cancelarRetiro(7L, 1L, true));
        assertTrue(service.cancelarRetiro(7L, 1L, true));

        assertEquals(EstadoRetiro.CANCELADO, estado);
        verify(repository, times(1)).marcarCancelado(7L, 1L);
        verify(repository, never()).marcarConcluido(anyLong(), anyLong());
    }

    @Test
    void habilitar_dos_veces_lo_deja_habilitado_y_escribe_una_sola_vez() {
        estado = EstadoRetiro.CANCELADO;

        assertTrue(service.cancelarRetiro(7L, 1L, false));
        assertTrue(service.cancelarRetiro(7L, 1L, false));

        assertEquals(EstadoRetiro.CONCLUIDO, estado);
        verify(repository, times(1)).marcarConcluido(7L, 1L);
        verify(repository, never()).marcarCancelado(anyLong(), anyLong());
    }

    @Test
    void sin_argumento_cancela_y_repetido_se_rechaza_en_vez_de_habilitar() {
        assertTrue(service.cancelarRetiro(7L, 1L, null));
        assertEquals(EstadoRetiro.CANCELADO, estado);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, null));

        assertTrue(e.getMessage().contains("ya está cancelado") && e.getMessage().contains("actualizá"), e.getMessage());
        assertEquals(EstadoRetiro.CANCELADO, estado);
        verify(repository, never()).marcarConcluido(anyLong(), anyLong());
    }

    @Test
    void toma_el_lock_antes_de_leer_el_estado() {
        service.cancelarRetiro(7L, 1L, true);

        InOrder orden = inOrder(repository);
        orden.verify(repository).lockByIdAndSucursalId(7L, 1L);
        orden.verify(repository).findSituacion(7L, 1L);
        orden.verify(repository).marcarCancelado(7L, 1L);
    }

    @Test
    void no_guarda_la_entidad_ni_avisa_retiro_realizado() {
        service.cancelarRetiro(7L, 1L, true);
        service.cancelarRetiro(7L, 1L, false);

        // Guardar la entidad reescribe la fila entera; y save() del servicio publica «RETIRO REALIZADO».
        verify(repository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void un_retiro_con_su_ingreso_en_la_caja_mayor_no_se_cancela() {
        movimientoId = 114L;
        cajaMayorId = 1L;

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("ya entró a la caja mayor"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_contado_en_cero_tampoco_se_cancela() {
        movimientoId = -1L;   // verificado sin plata: no hay movimiento, pero entró

        assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_con_verificacion_vigente_no_se_cancela_aunque_sus_campos_esten_limpios() {
        when(verificacionRepository.findVigente(7L, 1L)).thenReturn(Optional.of(new RetiroVerificacion()));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("anulá primero su verificación"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_en_estado_verificado_no_se_cancela_ni_sin_argumento() {
        estado = EstadoRetiro.VERIFICADO_CONCLUIDO_CON_PROBLEMA;

        assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));
        assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, null));
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_con_caja_mayor_asignada_y_sin_movimiento_no_se_cancela_y_no_habla_de_verificacion() {
        cajaMayorId = 1L;

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("caja mayor asignada"), e.getMessage());
        assertFalse(e.getMessage().contains("verificación"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_todavia_abierto_en_el_pdv_no_se_cancela() {
        estado = EstadoRetiro.EN_PROCESO;

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(7L, 1L, true));

        assertTrue(e.getMessage().contains("EN_PROCESO"), e.getMessage());
        assertNoSeEscribio();
    }

    @Test
    void un_retiro_concluido_se_cancela() {
        estado = EstadoRetiro.CONCLUIDO;

        service.cancelarRetiro(7L, 1L, true);

        assertEquals(EstadoRetiro.CANCELADO, estado);
    }

    @Test
    void habilitar_un_verificado_no_lo_baja_a_concluido() {
        estado = EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA;
        movimientoId = 114L;

        assertTrue(service.cancelarRetiro(7L, 1L, false));

        assertEquals(EstadoRetiro.VERIFICADO_CONCLUIDO_SIN_PROBLEMA, estado);
        assertNoSeEscribio();
    }

    @Test
    void un_cancelado_que_ya_tiene_su_ingreso_se_puede_habilitar() {
        // El caso que existe en producción: cancelado con la plata en la caja mayor. Habilitarlo lo arregla.
        estado = EstadoRetiro.CANCELADO;
        movimientoId = 114L;
        cajaMayorId = 1L;

        assertTrue(service.cancelarRetiro(7L, 1L, false));

        assertEquals(EstadoRetiro.CONCLUIDO, estado);
    }

    @Test
    void un_retiro_que_no_existe_lo_dice() {
        when(repository.lockByIdAndSucursalId(99L, 1L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.cancelarRetiro(99L, 1L, true));

        assertTrue(e.getMessage().contains("no encontrado"), e.getMessage());
    }

    // ── Retiros flotantes en el rol acotado (TESORERIA CAJA) ──

    private static final org.springframework.data.domain.Pageable PAGINA =
            org.springframework.data.domain.PageRequest.of(0, 20);

    @Test
    void flotantes_sin_rol_acotado_consulta_como_siempre() {
        service.findFlotantes(null, null, null, null, PAGINA);

        verify(repository).findFlotantes(null, null, null, null, PAGINA);
        verify(repository, never()).findFlotantesEnSucursales(any(), any(), any(), any(), any());
    }

    @Test
    void flotantes_acotado_sin_sucursal_pedida_usa_las_de_sus_cajas() {
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L));

        service.findFlotantes(null, null, null, null, PAGINA);

        verify(repository).findFlotantesEnSucursales(java.util.Arrays.asList(13L, 4L), null, null, null, PAGINA);
        verify(repository, never()).findFlotantes(any(), any(), any(), any(), any());
    }

    @Test
    void flotantes_acotado_con_una_sucursal_suya_filtra_por_esa() {
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L));

        service.findFlotantes(4L, null, null, null, PAGINA);

        verify(repository).findFlotantesEnSucursales(java.util.Collections.singletonList(4L), null, null, null, PAGINA);
    }

    @Test
    void flotantes_acotado_pidiendo_una_sucursal_ajena_no_devuelve_nada() {
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L));

        org.springframework.data.domain.Page<Retiro> pagina = service.findFlotantes(7L, null, null, null, PAGINA);

        assertEquals(0, pagina.getTotalElements());
        verifyNoInteractions(repository);
    }

    @Test
    void flotantes_acotado_sin_ninguna_sucursal_no_devuelve_nada_ni_consulta() {
        // Vacío es «ninguna»: jamás puede terminar en la consulta sin filtro.
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Collections.emptyList());

        org.springframework.data.domain.Page<Retiro> pagina = service.findFlotantes(null, null, null, null, PAGINA);

        assertEquals(0, pagina.getTotalElements());
        verifyNoInteractions(repository);
    }

    @Test
    void flotantes_acotado_con_caja_destino_deja_solo_las_sucursales_que_esa_caja_cubre() {
        // Opera dos cajas (sucursales 13, 4 y 9 entre las dos); la caja destino cubre solo 4 y 9.
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L, 9L));
        when(seguridad.sucursalesDeCajaIds(19L)).thenReturn(java.util.Arrays.asList(4L, 9L));

        service.findFlotantes(null, null, 19L, null, null, PAGINA);

        verify(repository).findFlotantesEnSucursales(java.util.Arrays.asList(4L, 9L), null, null, null, PAGINA);
    }

    @Test
    void flotantes_acotado_con_una_caja_destino_ajena_no_devuelve_nada() {
        // La caja destino cubre la 1, que no es de ninguna caja suya: no hay intersección.
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L));
        when(seguridad.sucursalesDeCajaIds(16L)).thenReturn(java.util.Collections.singletonList(1L));

        org.springframework.data.domain.Page<Retiro> pagina = service.findFlotantes(null, null, 16L, null, null, PAGINA);

        assertEquals(0, pagina.getTotalElements());
        verifyNoInteractions(repository);
    }

    @Test
    void flotantes_sin_rol_acotado_ignora_la_caja_destino() {
        service.findFlotantes(null, null, 19L, null, null, PAGINA);

        verify(repository).findFlotantes(null, null, null, null, PAGINA);
    }

    // ── Retiros ya procesados de una caja (aviso del diálogo de ingreso) ──

    private static final org.springframework.data.domain.Pageable TOPE =
            org.springframework.data.domain.PageRequest.of(0, RetiroService.TOPE_INGRESADOS);

    @Test
    void ingresados_sin_rol_acotado_pasa_la_sucursal_tal_cual_con_el_tope() {
        service.findIngresadosDeCaja(662L, 24L, 19L);

        verify(repository).findIngresadosDeCaja(662L, 24L, TOPE);
        verify(repository, never()).findIngresadosDeCajaEnSucursales(any(), any(), any());
    }

    @Test
    void ingresados_acotado_con_caja_destino_deja_solo_las_sucursales_que_esa_caja_cubre() {
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L, 9L));
        when(seguridad.sucursalesDeCajaIds(19L)).thenReturn(java.util.Arrays.asList(4L, 9L));

        service.findIngresadosDeCaja(662L, null, 19L);

        verify(repository).findIngresadosDeCajaEnSucursales(662L, java.util.Arrays.asList(4L, 9L), TOPE);
        verify(repository, never()).findIngresadosDeCaja(any(), any(), any());
    }

    @Test
    void ingresados_acotado_pidiendo_una_sucursal_ajena_no_devuelve_nada() {
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Arrays.asList(13L, 4L));

        assertTrue(service.findIngresadosDeCaja(662L, 7L, null).isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void ingresados_acotado_sin_ninguna_sucursal_no_devuelve_nada_ni_consulta() {
        when(seguridad.esSoloCaja()).thenReturn(true);
        when(seguridad.sucursalesOperablesIds()).thenReturn(java.util.Collections.emptyList());

        assertTrue(service.findIngresadosDeCaja(662L, null, null).isEmpty());
        verifyNoInteractions(repository);
    }

    // ── Montos del aviso: lo que entró a caja mayor ──

    private static com.franco.dev.repository.financiero.RetiroIngresadoDto ingresado(long retiroId, long suc) {
        return new com.franco.dev.repository.financiero.RetiroIngresadoDto(retiroId, suc, "SUC", 662L, 3L, "TESORERIA",
                90L, "JUAN", java.time.LocalDateTime.now());
    }

    private static Object[] fila(long retiroId, long suc, long moneda, double suma) {
        return new Object[]{retiroId, suc, moneda, suma};
    }

    private java.util.List<com.franco.dev.repository.financiero.RetiroIngresadoDto> conLista(
            com.franco.dev.repository.financiero.RetiroIngresadoDto... l) {
        when(repository.findIngresadosDeCaja(662L, null, TOPE)).thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList(l)));
        return service.findIngresadosDeCaja(662L, null, null);
    }

    @Test
    void ingresados_toman_lo_declarado_por_moneda_sin_mezclar_el_mismo_id_de_otra_sucursal() {
        when(detalleRepository.sumarPorMoneda(any())).thenReturn(java.util.Arrays.asList(
                fila(10, 24, 1, 150000), fila(10, 24, 2, 30), fila(10, 13, 1, 777), fila(11, 24, 3, 20)));

        java.util.List<com.franco.dev.repository.financiero.RetiroIngresadoDto> l =
                conLista(ingresado(10, 24), ingresado(11, 24));

        assertEquals(150000.0, l.get(0).getRetiroGs());
        assertEquals(30.0, l.get(0).getRetiroRs());
        assertNull(l.get(0).getRetiroDs());
        assertNull(l.get(1).getRetiroGs());
        assertEquals(20.0, l.get(1).getRetiroDs());
    }

    @Test
    void ingresados_con_verificacion_vigente_toman_solo_lo_contado() {
        // Declaró 800.000 Gs; la verificación contó 850.000 Gs y 50 R$, y eso es lo que entró.
        when(detalleRepository.sumarPorMoneda(any())).thenReturn(java.util.Arrays.asList(
                fila(10, 24, 1, 800000), fila(10, 24, 3, 5)));
        when(verificacionRepository.sumarContadoVigente(any())).thenReturn(java.util.Arrays.asList(
                fila(10, 24, 1, 850000), fila(10, 24, 2, 50)));

        com.franco.dev.repository.financiero.RetiroIngresadoDto r = conLista(ingresado(10, 24)).get(0);

        assertEquals(850000.0, r.getRetiroGs());
        assertEquals(50.0, r.getRetiroRs());
        assertNull(r.getRetiroDs(), "lo declarado no se mezcla con lo contado");
    }

    @Test
    void ingresados_sin_detalle_quedan_sin_montos() {
        com.franco.dev.repository.financiero.RetiroIngresadoDto r = conLista(ingresado(10, 24)).get(0);

        assertNull(r.getRetiroGs());
        assertNull(r.getRetiroRs());
        assertNull(r.getRetiroDs());
    }

    @Test
    void ingresados_sin_resultados_no_buscan_montos() {
        assertTrue(conLista().isEmpty());
        verifyNoInteractions(detalleRepository);
        verify(verificacionRepository, never()).sumarContadoVigente(any());
    }
}
