package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.repository.operaciones.LoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lotes fase 3, entrega 3: eliminar lote creado por error = baja logica restaurable. */
class LoteEliminarTest {

    private LoteRepository repo;
    private LoteService service;
    private Lote lote;

    @BeforeEach
    void setUp() {
        repo = mock(LoteRepository.class);
        service = new LoteService(repo);
        lote = new Lote();
        lote.setId(7L);
        lote.setNumeroLote("ERR-1");
        lote.setEstado(EstadoLote.LIBERADO);
        when(repo.findById(7L)).thenReturn(Optional.of(lote));
        when(repo.save(any(Lote.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void reglaPura() {
        assertDoesNotThrow(() -> LoteService.validarEliminable(0, "mal cargado"));
        assertThrows(IllegalArgumentException.class, () -> LoteService.validarEliminable(1, "x"));
        assertThrows(IllegalArgumentException.class, () -> LoteService.validarEliminable(0, " "));
        assertThrows(IllegalArgumentException.class, () -> LoteService.validarEliminable(0, null));
    }

    @Test
    void eliminarSinReferenciasGuardaCopiaYBloquea() {
        when(repo.estaEliminado(7L)).thenReturn(false);
        when(repo.contarReferencias(7L)).thenReturn(0L);
        assertTrue(service.eliminar(7L, " numero mal tipeado ", null));
        verify(repo).registrarEliminado(7L, null, "numero mal tipeado");
        assertEquals(EstadoLote.BLOQUEADO, lote.getEstado());
        verify(repo, never()).deleteById(anyLong());
        verify(repo, never()).delete(any(Lote.class));
    }

    @Test
    void conReferenciasNoSeElimina() {
        when(repo.estaEliminado(7L)).thenReturn(false);
        when(repo.contarReferencias(7L)).thenReturn(3L);
        assertThrows(IllegalArgumentException.class, () -> service.eliminar(7L, "x", null));
        verify(repo, never()).registrarEliminado(anyLong(), any(), anyString());
        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
    }

    @Test
    void yaEliminadoNoSeEliminaDeNuevo() {
        when(repo.estaEliminado(7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.eliminar(7L, "x", null));
    }

    @Test
    void restaurarDevuelveEstadoAnterior() {
        lote.setEstado(EstadoLote.BLOQUEADO);
        when(repo.estadoAnteriorEliminado(7L)).thenReturn("CUARENTENA");
        assertTrue(service.restaurar(7L));
        assertEquals(EstadoLote.CUARENTENA, lote.getEstado());
        verify(repo).borrarEliminado(7L);
    }

    @Test
    void restaurarUnLoteNoEliminadoFalla() {
        when(repo.estadoAnteriorEliminado(7L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.restaurar(7L));
        verify(repo, never()).borrarEliminado(anyLong());
    }

    @Test
    void cambiarEstadoDeEliminadoSeNiega() {
        when(repo.estaEliminado(7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.cambiarEstado(7L, EstadoLote.LIBERADO, null, null));
        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
        verify(repo, never()).save(any(Lote.class));
    }

    @Test
    void verificarNoEliminado() {
        when(repo.estaEliminado(7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.verificarNoEliminado(lote));
        when(repo.estaEliminado(7L)).thenReturn(false);
        assertDoesNotThrow(() -> service.verificarNoEliminado(lote));
    }
}
