package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.NotaCreditoDevolucion;
import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.NotaCreditoDevolucionItemRepository;
import com.franco.dev.repository.operaciones.NotaCreditoDevolucionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #207: revertir el credito borraba la nota de credito (y sus items) en vez
 * de anularla. Reproducido el 2026-10-31 en local con la nota TEST-208 del retiro 7:
 * despues de revertir, operaciones.nota_credito_devolucion quedaba con 0 filas.
 */
class NotaCreditoDevolucionAnulacionTest {

    private NotaCreditoDevolucionRepository repo;
    private NotaCreditoDevolucionItemRepository itemRepo;
    private DevolucionService devolucionService;
    private NotaCreditoDevolucionService service;

    @BeforeEach
    void setUp() {
        repo = mock(NotaCreditoDevolucionRepository.class);
        itemRepo = mock(NotaCreditoDevolucionItemRepository.class);
        devolucionService = mock(DevolucionService.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new NotaCreditoDevolucionService();
        ReflectionTestUtils.setField(service, "repository", repo);
        ReflectionTestUtils.setField(service, "itemRepository", itemRepo);
        ReflectionTestUtils.setField(service, "devolucionService", devolucionService);
    }

    private NotaCreditoDevolucion nota(String estado) {
        RetiroDevolucion retiro = new RetiroDevolucion();
        retiro.setId(7L);
        NotaCreditoDevolucion n = new NotaCreditoDevolucion();
        n.setId(1L);
        n.setRetiro(retiro);
        n.setNroNotaCredito("TEST-208");
        n.setMontoTotal(33091.3663);
        n.setSaldo(33091.3663);
        n.setEstado(estado);
        when(repo.findById(1L)).thenReturn(Optional.of(n));
        return n;
    }

    private Devolucion acreditada() {
        Devolucion d = new Devolucion();
        d.setId(3L);
        d.setEstado(DevolucionEstado.ACREDITADO);
        d.setResolucion(TipoResolucionDevolucion.NOTA_CREDITO);
        d.setFinalizado(true);
        d.setNroNotaCredito("TEST-208");
        d.setMontoAcreditado(33091.3663);
        when(devolucionService.findByRetiroId(7L)).thenReturn(Collections.singletonList(d));
        return d;
    }

    @Test
    void revertir_anulaLaNotaYNoLaBorra() {
        NotaCreditoDevolucion n = nota(NotaCreditoDevolucion.ESTADO_DISPONIBLE);
        acreditada();
        Usuario usuario = new Usuario();
        usuario.setId(10L);

        assertTrue(service.revertir(1L, usuario));

        verify(repo, never()).deleteById(anyLong());
        verify(repo, never()).delete(any());
        verify(itemRepo, never()).deleteAll(any());
        verify(repo).save(n);
        assertEquals(NotaCreditoDevolucion.ESTADO_ANULADA, n.getEstado());
        // El numero y el monto del proveedor quedan para auditar.
        assertEquals("TEST-208", n.getNroNotaCredito());
        assertEquals(33091.3663, n.getMontoTotal(), 0.0001);
        // Sin saldo: una nota anulada nunca se puede aplicar.
        assertEquals(0.0, n.getSaldo(), 0.0001);
        assertSame(usuario, n.getAnuladoPor());
        assertNotNull(n.getAnuladoEn());
    }

    @Test
    void revertir_devuelveLasDevolucionesARetirado() {
        nota(NotaCreditoDevolucion.ESTADO_DISPONIBLE);
        Devolucion d = acreditada();

        service.revertir(1L, null);

        assertEquals(DevolucionEstado.RETIRADO, d.getEstado());
        assertNull(d.getResolucion());
        assertFalse(d.getFinalizado());
        assertNull(d.getNroNotaCredito());
        assertNull(d.getMontoAcreditado());
    }

    @Test
    void revertir_unaNotaYaAnulada_falla() {
        nota(NotaCreditoDevolucion.ESTADO_ANULADA);

        assertThrows(GraphQLException.class, () -> service.revertir(1L, null));
        verify(repo, never()).save(any());
    }

    @Test
    void findByRetiroId_devuelveSoloLaVigente() {
        NotaCreditoDevolucion vigente = new NotaCreditoDevolucion();
        when(repo.findFirstByRetiroIdAndEstadoNotOrderByIdDesc(7L, NotaCreditoDevolucion.ESTADO_ANULADA))
                .thenReturn(Optional.of(vigente));

        assertSame(vigente, service.findByRetiroId(7L).orElse(null));
    }
}
