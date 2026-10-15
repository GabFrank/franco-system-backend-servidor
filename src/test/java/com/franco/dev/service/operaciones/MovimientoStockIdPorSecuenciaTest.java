package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.productos.PresentacionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El id de un movimiento nuevo sale de la secuencia, una vez, y uno existente conserva el suyo.
 *
 * Antes se calculaba con MAX(id) + 1 por sucursal y dos guardados simultaneos repetian el id
 * (issue #153). Que la secuencia no repite bajo concurrencia lo prueba
 * {@link MovimientoStockIdConcurrenteIT} contra una base real; aca se fija que nadie vuelva a
 * calcular el id a mano ni le cambie el id a un movimiento que ya existe, que seria pisar otro.
 */
class MovimientoStockIdPorSecuenciaTest {

    @Test
    void movimientoNuevoTomaElIdDeLaSecuencia() {
        MovimientoStockRepository repository = mock(MovimientoStockRepository.class);
        when(repository.siguienteId()).thenReturn(23894289L);
        when(repository.save(any(MovimientoStock.class))).thenAnswer(i -> i.getArgument(0));

        MovimientoStock nuevo = new MovimientoStock();
        nuevo.setSucursalId(1L);
        MovimientoStock guardado = servicio(repository).save(nuevo);

        assertEquals(23894289L, guardado.getId());
        verify(repository, times(1)).siguienteId();
    }

    @Test
    void movimientoExistenteConservaSuId() {
        MovimientoStockRepository repository = mock(MovimientoStockRepository.class);
        when(repository.save(any(MovimientoStock.class))).thenAnswer(i -> i.getArgument(0));

        MovimientoStock existente = new MovimientoStock();
        existente.setId(40L);
        existente.setSucursalId(1L);
        MovimientoStock guardado = servicio(repository).save(existente);

        assertEquals(40L, guardado.getId());
        verify(repository, never()).siguienteId();
    }

    @Test
    void filaDeLoteNuevaTomaElIdDeSuSecuencia() {
        MovimientoStockLoteRepository repository = mock(MovimientoStockLoteRepository.class);
        when(repository.siguienteId()).thenReturn(5001L);
        when(repository.save(any(MovimientoStockLote.class))).thenAnswer(i -> i.getArgument(0));

        MovimientoStockLote nueva = new MovimientoStockLote();
        nueva.setSucursalId(1L);
        MovimientoStockLote guardada = servicioLote(repository).save(nueva);

        assertEquals(5001L, guardada.getId());
        verify(repository, times(1)).siguienteId();
    }

    @Test
    void filaDeLoteExistenteConservaSuId() {
        MovimientoStockLoteRepository repository = mock(MovimientoStockLoteRepository.class);
        when(repository.save(any(MovimientoStockLote.class))).thenAnswer(i -> i.getArgument(0));

        MovimientoStockLote existente = new MovimientoStockLote();
        existente.setId(8L);
        existente.setSucursalId(1L);
        MovimientoStockLote guardada = servicioLote(repository).save(existente);

        assertEquals(8L, guardada.getId());
        verify(repository, never()).siguienteId();
    }

    private MovimientoStockService servicio(MovimientoStockRepository repository) {
        // @AllArgsConstructor (orden de declaración): repository, modificacionService, sucursalService,
        // movimientoStockLoteService, loteFefoService, transferenciaItemLoteService.
        return new MovimientoStockService(
                repository,
                mock(ModificacionService.class),
                mock(SucursalService.class),
                mock(MovimientoStockLoteService.class),
                mock(LoteFefoService.class),
                mock(TransferenciaItemLoteService.class));
    }

    private MovimientoStockLoteService servicioLote(MovimientoStockLoteRepository repository) {
        return new MovimientoStockLoteService(repository, mock(LoteService.class), mock(PresentacionService.class));
    }
}
