package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.service.operaciones.VentaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code cancelarVentaCredito} solo sincroniza la venta credito con su venta. Hasta la issue #339,
 * llamado sin venta alternaba el estado de la venta con un save directo, sin tocar caja, stock,
 * tarjeta, delivery ni factura.
 */
class VentaCreditoServiceCancelarTest {

    private static final Long ID = 9L;
    private static final Long SUCURSAL_ID = 1L;

    private VentaService ventaService;
    private VentaCreditoRepository repository;
    private VentaCreditoService service;
    private VentaCredito ventaCredito;

    @BeforeEach
    void setUp() {
        ventaService = mock(VentaService.class);
        repository = mock(VentaCreditoRepository.class);
        service = new VentaCreditoService(ventaService, repository);

        Venta ventaDeLaVentaCredito = new Venta();
        ventaDeLaVentaCredito.setId(40L);
        ventaDeLaVentaCredito.setSucursalId(SUCURSAL_ID);
        ventaDeLaVentaCredito.setEstado(VentaEstado.CONCLUIDA);
        ventaCredito = new VentaCredito();
        ventaCredito.setId(ID);
        ventaCredito.setSucursalId(SUCURSAL_ID);
        ventaCredito.setEstado(EstadoVentaCredito.ABIERTO);
        ventaCredito.setVenta(ventaDeLaVentaCredito);
        when(repository.findById(any(EmbebedPrimaryKey.class))).thenReturn(Optional.of(ventaCredito));
        when(ventaService.findById(any(EmbebedPrimaryKey.class)))
                .thenReturn(Optional.of(ventaDeLaVentaCredito));
        when(ventaService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Venta venta(VentaEstado estado) {
        Venta venta = new Venta();
        venta.setId(40L);
        venta.setSucursalId(SUCURSAL_ID);
        venta.setEstado(estado);
        return venta;
    }

    @Test
    @DisplayName("sin venta no cancela nada: ni la venta ni la venta credito")
    void sinVentaNoCancelaNada() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.cancelarVentaCredito(ID, SUCURSAL_ID, null));

        assertTrue(e.getMessage().contains("Falta la venta"), e.getMessage());
        assertEquals(VentaEstado.CONCLUIDA, ventaCredito.getVenta().getEstado());
        assertEquals(EstadoVentaCredito.ABIERTO, ventaCredito.getEstado());
        verify(ventaService, never()).save(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("con la venta cancelada, la venta credito queda CANCELADO")
    void ventaCanceladaDejaCancelado() {
        assertTrue(service.cancelarVentaCredito(ID, SUCURSAL_ID, venta(VentaEstado.CANCELADA)));

        assertEquals(EstadoVentaCredito.CANCELADO, ventaCredito.getEstado());
        verify(repository, times(1)).save(ventaCredito);
        verify(ventaService, never()).save(any());
    }

    @Test
    @DisplayName("con la venta reactivada, la venta credito vuelve a ABIERTO")
    void ventaReactivadaDejaAbierto() {
        ventaCredito.setEstado(EstadoVentaCredito.CANCELADO);

        assertTrue(service.cancelarVentaCredito(ID, SUCURSAL_ID, venta(VentaEstado.CONCLUIDA)));

        assertEquals(EstadoVentaCredito.ABIERTO, ventaCredito.getEstado());
        verify(repository, times(1)).save(ventaCredito);
    }
}
