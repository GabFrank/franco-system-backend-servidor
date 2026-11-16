package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.ColectaDevolucion;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.domain.operaciones.dto.RetiroBloqueResultadoDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la orquestacion de operaciones de devolucion: colecta en bloque
 * (agrupacion por origen), y revert seguro (linea y operacion completa),
 * ademas del guard de sucursal servidor. Unit tests con Mockito (sin DB).
 *
 * Constructor Lombok @AllArgsConstructor (orden): repository, devolucionItemService,
 * devolucionItemRepository, movimientoStockService, productoVencimientoService,
 * gastoService, gastoRepository, tipoGastoRepository, codigoService, applicationContext.
 */
class DevolucionOperacionesTest {

    private Sucursal sucursal(long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre("SUC-" + id);
        return s;
    }

    private Devolucion dev(long id, DevolucionEstado estado, Sucursal origen) {
        Devolucion d = new Devolucion();
        d.setId(id);
        d.setEstado(estado);
        d.setSucursalOrigen(origen);
        return d;
    }

    /** Con proveedor: la colecta interna solo aplica a devoluciones CON_PROVEEDOR. */
    private Devolucion devConProveedor(long id, DevolucionEstado estado, Sucursal origen) {
        Devolucion d = dev(id, estado, origen);
        d.setTipo(TipoDevolucion.CON_PROVEEDOR);
        return d;
    }

    private DevolucionService build(DevolucionRepository repo, DevolucionItemService itemService,
                                    DevolucionServiceDePrueba deps) {
        return deps.con(repo).con(itemService).build();
    }

    // ===================== colectarEnBloque =====================

    @Test
    void colecta_agrupaUnaCabeceraPorOrigen() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Sucursal s10 = sucursal(10), s20 = sucursal(20), destino = sucursal(99);
        when(repo.findById(1L)).thenReturn(Optional.of(devConProveedor(1L, DevolucionEstado.SEPARADO, s10)));
        when(repo.findById(2L)).thenReturn(Optional.of(devConProveedor(2L, DevolucionEstado.SEPARADO, s10)));
        when(repo.findById(3L)).thenReturn(Optional.of(devConProveedor(3L, DevolucionEstado.SEPARADO, s20)));

        SucursalService sucursalService = mock(SucursalService.class);
        when(sucursalService.findById(99L)).thenReturn(Optional.of(destino));

        ColectaDevolucionService cs = mock(ColectaDevolucionService.class);
        AtomicLong seq = new AtomicLong(0);
        when(cs.crear(any(), any(), any())).thenAnswer(inv -> {
            ColectaDevolucion c = new ColectaDevolucion();
            c.setId(seq.incrementAndGet());
            return c;
        });

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(sucursalService);
        deps.con(cs);

        DevolucionService service = build(repo, null, deps);
        RetiroBloqueResultadoDto res = service.colectarEnBloque(Arrays.asList(1L, 2L, 3L), 99L, null);

        assertEquals(3, res.getResultados().size());
        assertTrue(res.getResultados().stream().allMatch(r -> r.getOk()));
        // Dos origenes distintos (10 y 20) -> dos cabeceras.
        verify(cs, times(2)).crear(any(), any(), any());
        // Cada linea paso por colectarLinea: quedo guardada COLECTADO en el destino.
        org.mockito.ArgumentCaptor<Devolucion> guardadas = org.mockito.ArgumentCaptor.forClass(Devolucion.class);
        verify(repo, org.mockito.Mockito.atLeast(3)).save(guardadas.capture());
        assertEquals(new java.util.HashSet<>(Arrays.asList(1L, 2L, 3L)), guardadas.getAllValues().stream()
                .filter(d -> d.getEstado() == DevolucionEstado.COLECTADO
                        && d.getSucursalUbicacion().getId() == 99L)
                .map(Devolucion::getId)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void colecta_lineaQueFallaNoAbortaResto() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Sucursal s10 = sucursal(10), destino = sucursal(99);
        // La 2 sigue PENDIENTE: COLECTADO solo desde SEPARADO, asi que su linea falla.
        when(repo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.of(devConProveedor(id,
                    id == 2L ? DevolucionEstado.PENDIENTE : DevolucionEstado.SEPARADO, s10));
        });

        SucursalService sucursalService = mock(SucursalService.class);
        when(sucursalService.findById(99L)).thenReturn(Optional.of(destino));
        ColectaDevolucionService cs = mock(ColectaDevolucionService.class);
        ColectaDevolucion header = new ColectaDevolucion();
        header.setId(7L);
        when(cs.crear(any(), any(), any())).thenReturn(header);

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(sucursalService);
        deps.con(cs);

        DevolucionService service = build(repo, null, deps);
        RetiroBloqueResultadoDto res = service.colectarEnBloque(Arrays.asList(1L, 2L, 3L), 99L, null);

        assertTrue(res.getResultados().get(0).getOk());
        assertFalse(res.getResultados().get(1).getOk());
        assertTrue(res.getResultados().get(2).getOk());
        // La cabecera recibio lineas ok (1 y 3), no se elimina.
        verify(cs, never()).delete(anyLong());
    }

    @Test
    void colecta_cabeceraVaciaSeElimina() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Sucursal s10 = sucursal(10), destino = sucursal(99);
        // PENDIENTE: COLECTADO solo desde SEPARADO, asi que la unica linea falla.
        when(repo.findById(1L)).thenReturn(Optional.of(devConProveedor(1L, DevolucionEstado.PENDIENTE, s10)));

        SucursalService sucursalService = mock(SucursalService.class);
        when(sucursalService.findById(99L)).thenReturn(Optional.of(destino));
        ColectaDevolucionService cs = mock(ColectaDevolucionService.class);
        ColectaDevolucion header = new ColectaDevolucion();
        header.setId(77L);
        when(cs.crear(any(), any(), any())).thenReturn(header);

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(sucursalService);
        deps.con(cs);

        DevolucionService service = build(repo, null, deps);
        RetiroBloqueResultadoDto res = service.colectarEnBloque(Collections.singletonList(1L), 99L, null);

        assertFalse(res.getResultados().get(0).getOk());
        verify(cs).delete(77L);
    }

    @Test
    void colecta_destinoNuloFalla() {
        DevolucionService service = build(mock(DevolucionRepository.class), null, new DevolucionServiceDePrueba());
        assertThrows(GraphQLException.class,
                () -> service.colectarEnBloque(Arrays.asList(1L), null, null));
    }

    @Test
    void colecta_listaVaciaResultadoVacio() {
        DevolucionService service = build(mock(DevolucionRepository.class), null, new DevolucionServiceDePrueba());
        assertTrue(service.colectarEnBloque(new ArrayList<>(), 99L, null).getResultados().isEmpty());
    }

    // ===================== revertirEstado (linea) =====================

    @Test
    void revertir_retiradoNoColectada_vuelveASeparado_yMarcaCabecera() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.countByRetiroId(5L)).thenReturn(0L);

        Sucursal origen = sucursal(10);
        Devolucion d = dev(1L, DevolucionEstado.RETIRADO, origen);
        d.setColectadoEn(null); // no fue colectada
        RetiroDevolucion retiro = new RetiroDevolucion();
        retiro.setId(5L);
        d.setRetiro(retiro);
        when(repo.findById(1L)).thenReturn(Optional.of(d));

        RetiroDevolucionService rs = mock(RetiroDevolucionService.class);
        when(rs.findById(5L)).thenReturn(Optional.of(retiro));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(rs);

        DevolucionService service = build(repo, null, deps);
        Devolucion res = service.revertirEstado(1L, null);

        assertEquals(DevolucionEstado.SEPARADO, res.getEstado());
        assertNull(res.getRetiro());
        verify(rs).save(argThat(h -> RetiroDevolucion.ESTADO_REVERTIDO.equals(h.getEstado())));
    }

    @Test
    void revertir_retiradoColectada_vuelveAColectado() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.countByRetiroId(anyLong())).thenReturn(1L); // quedan otras lineas

        Sucursal origen = sucursal(10), deposito = sucursal(99);
        Devolucion d = dev(1L, DevolucionEstado.RETIRADO, origen);
        d.setColectadoEn(java.time.LocalDateTime.now());
        d.setSucursalUbicacion(deposito);
        ColectaDevolucion colecta = new ColectaDevolucion();
        colecta.setId(3L);
        d.setColecta(colecta); // tiene cabecera de colecta -> fue colectada
        RetiroDevolucion retiro = new RetiroDevolucion();
        retiro.setId(5L);
        d.setRetiro(retiro);
        when(repo.findById(1L)).thenReturn(Optional.of(d));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        DevolucionService service = build(repo, null, deps);
        Devolucion res = service.revertirEstado(1L, null);

        assertEquals(DevolucionEstado.COLECTADO, res.getEstado());
        assertNull(res.getRetiro());
        assertNotNull(res.getColecta()); // sigue perteneciendo a la colecta
    }

    @Test
    void revertir_colectado_vuelveASeparado_reseteaUbicacion_yDesvincula() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.countByColectaId(8L)).thenReturn(0L);

        Sucursal origen = sucursal(10), deposito = sucursal(99);
        Devolucion d = dev(1L, DevolucionEstado.COLECTADO, origen);
        d.setSucursalUbicacion(deposito);
        d.setColectadoEn(java.time.LocalDateTime.now());
        ColectaDevolucion colecta = new ColectaDevolucion();
        colecta.setId(8L);
        d.setColecta(colecta);
        when(repo.findById(1L)).thenReturn(Optional.of(d));

        ColectaDevolucionService cs = mock(ColectaDevolucionService.class);
        when(cs.findById(8L)).thenReturn(Optional.of(colecta));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(cs);

        DevolucionService service = build(repo, null, deps);
        Devolucion res = service.revertirEstado(1L, null);

        assertEquals(DevolucionEstado.SEPARADO, res.getEstado());
        assertEquals(10L, res.getSucursalUbicacion().getId().longValue()); // reseteada a origen
        assertNull(res.getColectadoEn());
        assertNull(res.getColecta());
        verify(cs).save(argThat(h -> ColectaDevolucion.ESTADO_REVERTIDO.equals(h.getEstado())));
    }

    @Test
    void revertir_separado_vuelveAPendiente() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Sucursal origen = sucursal(10);
        Devolucion d = dev(1L, DevolucionEstado.SEPARADO, origen);
        d.setSucursalUbicacion(origen);
        when(repo.findById(1L)).thenReturn(Optional.of(d));

        DevolucionItemService itemService = mock(DevolucionItemService.class);
        when(itemService.findByDevolucionId(1L)).thenReturn(new ArrayList<>()); // sin items -> no mueve stock

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        DevolucionService service = build(repo, itemService, deps);
        Devolucion res = service.revertirEstado(1L, null);

        assertEquals(DevolucionEstado.PENDIENTE, res.getEstado());
    }

    @Test
    void revertir_estadoNoReversible_lanza() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        Devolucion d = dev(1L, DevolucionEstado.ACREDITADO, sucursal(10));
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        DevolucionService service = build(repo, null, new DevolucionServiceDePrueba());
        assertThrows(GraphQLException.class, () -> service.revertirEstado(1L, null));
    }

    // ===================== revertirRetiro / revertirColecta (operacion) =====================

    @Test
    void revertirRetiro_revierteTodasLasLineas_yMarcaCabecera() {
        RetiroDevolucion header = new RetiroDevolucion();
        header.setId(5L);
        RetiroDevolucionService rs = mock(RetiroDevolucionService.class);
        when(rs.findById(5L)).thenReturn(Optional.of(header));
        when(rs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Devolucion d1 = dev(1L, DevolucionEstado.RETIRADO, sucursal(10));
        Devolucion d2 = dev(2L, DevolucionEstado.RETIRADO, sucursal(10));
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.findByRetiroId(5L)).thenReturn(Arrays.asList(d1, d2));
        when(repo.findById(1L)).thenReturn(Optional.of(d1));
        when(repo.findById(2L)).thenReturn(Optional.of(d2));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(rs);

        DevolucionService service = build(repo, null, deps);
        RetiroDevolucion res = service.revertirRetiro(5L, null);

        // Cada linea paso por revertirEstado: RETIRADO sin colecta -> SEPARADO.
        assertEquals(DevolucionEstado.SEPARADO, d1.getEstado());
        assertEquals(DevolucionEstado.SEPARADO, d2.getEstado());
        assertEquals(RetiroDevolucion.ESTADO_REVERTIDO, res.getEstado());
    }

    @Test
    void revertirColecta_revierteTodasLasLineas_yMarcaCabecera() {
        ColectaDevolucion header = new ColectaDevolucion();
        header.setId(8L);
        ColectaDevolucionService cs = mock(ColectaDevolucionService.class);
        when(cs.findById(8L)).thenReturn(Optional.of(header));
        when(cs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Devolucion d1 = dev(1L, DevolucionEstado.COLECTADO, sucursal(10));
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.findByColectaId(8L)).thenReturn(Collections.singletonList(d1));
        when(repo.findById(1L)).thenReturn(Optional.of(d1));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(cs);

        DevolucionService service = build(repo, null, deps);
        ColectaDevolucion res = service.revertirColecta(8L, null);

        // La linea paso por revertirEstado: COLECTADO -> SEPARADO.
        assertEquals(DevolucionEstado.SEPARADO, d1.getEstado());
        assertEquals(ColectaDevolucion.ESTADO_REVERTIDO, res.getEstado());
    }

    @Test
    void revertirColecta_conLineaRetirada_bloquea() {
        ColectaDevolucion header = new ColectaDevolucion();
        header.setId(8L);
        ColectaDevolucionService cs = mock(ColectaDevolucionService.class);
        when(cs.findById(8L)).thenReturn(Optional.of(header));

        DevolucionRepository repo = mock(DevolucionRepository.class);
        // Una linea sigue COLECTADO, otra ya fue RETIRADA -> debe bloquear.
        when(repo.findByColectaId(8L)).thenReturn(Arrays.asList(
                dev(1L, DevolucionEstado.COLECTADO, sucursal(10)),
                dev(2L, DevolucionEstado.RETIRADO, sucursal(10))));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(cs);

        DevolucionService service = build(repo, null, deps);
        assertThrows(GraphQLException.class, () -> service.revertirColecta(8L, null));
        // No debe revertir ninguna linea ni marcar la cabecera.
        verify(repo, never()).save(any());
        verify(cs, never()).save(any());
    }

    @Test
    void revertirRetiro_conLineaCanjeada_bloquea() {
        RetiroDevolucion header = new RetiroDevolucion();
        header.setId(5L);
        RetiroDevolucionService rs = mock(RetiroDevolucionService.class);
        when(rs.findById(5L)).thenReturn(Optional.of(header));

        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.findByRetiroId(5L)).thenReturn(Arrays.asList(
                dev(1L, DevolucionEstado.RETIRADO, sucursal(10)),
                dev(2L, DevolucionEstado.CANJEADO, sucursal(10))));

        DevolucionServiceDePrueba deps = new DevolucionServiceDePrueba();
        deps.con(rs);

        DevolucionService service = build(repo, null, deps);
        assertThrows(GraphQLException.class, () -> service.revertirRetiro(5L, null));
        verify(repo, never()).save(any());
        verify(rs, never()).save(any());
    }

    // ===================== guard sucursal servidor =====================

    @Test
    void save_rechazaSucursalServidor() {
        DevolucionService service = build(mock(DevolucionRepository.class), null, new DevolucionServiceDePrueba());
        Devolucion nueva = new Devolucion(); // id null
        nueva.setSucursalOrigen(sucursal(0)); // servidor
        assertThrows(GraphQLException.class, () -> service.save(nueva));
    }

    @Test
    void save_rechazaSucursalNula() {
        DevolucionService service = build(mock(DevolucionRepository.class), null, new DevolucionServiceDePrueba());
        Devolucion nueva = new Devolucion(); // id null, sin origen
        assertThrows(GraphQLException.class, () -> service.save(nueva));
    }
}
