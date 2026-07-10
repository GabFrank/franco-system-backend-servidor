package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.RetiroBloqueResultadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroDevolucionResultadoDto;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test de la logica de retiro en bloque (retirarEnBloque).
 *
 * Contrato clave: el fallo de una devolucion (ej. stock insuficiente) NO aborta
 * el resto; cada una se reporta por id (ok / mensaje).
 *
 * retirarEnBloque delega en avanzarEstado a traves del proxy Spring
 * (applicationContext.getBean(DevolucionService.class)) para que aplique su
 * @Transactional por devolucion. Aca se mockea ese "self" para aislar la logica
 * de agregacion de resultados.
 *
 * Nota: la construccion usa el constructor generado por Lombok @AllArgsConstructor
 * (orden de declaracion de campos: repository, devolucionItemService,
 * devolucionItemRepository, movimientoStockService, productoVencimientoService,
 * gastoService, gastoRepository, tipoGastoRepository, codigoService,
 * applicationContext). Solo importa que applicationContext sea el ultimo; el
 * resto van null porque el test solo ejercita retirarEnBloque.
 */
class DevolucionRetiroEnBloqueTest {

    private DevolucionService buildService(DevolucionService self) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(DevolucionService.class)).thenReturn(self);
        return new DevolucionService(null, null, null, null, null, null, null, null, null, ctx);
    }

    @Test
    void unaFallaNoAbortaLasDemas() {
        DevolucionService self = mock(DevolucionService.class);
        // La devolucion 2 no tiene stock suficiente -> lanza; 1 y 3 avanzan ok.
        when(self.avanzarEstado(eq(2L), any(), any()))
                .thenThrow(new GraphQLException("Stock insuficiente"));

        DevolucionService service = buildService(self);

        RetiroBloqueResultadoDto res =
                service.retirarEnBloque(Arrays.asList(1L, 2L, 3L), null);

        assertEquals(3, res.getResultados().size());
        RetiroDevolucionResultadoDto r1 = res.getResultados().get(0);
        RetiroDevolucionResultadoDto r2 = res.getResultados().get(1);
        RetiroDevolucionResultadoDto r3 = res.getResultados().get(2);

        assertEquals(1L, r1.getId().longValue());
        assertTrue(r1.getOk());

        assertEquals(2L, r2.getId().longValue());
        assertFalse(r2.getOk());
        assertEquals("Stock insuficiente", r2.getMensaje());

        assertEquals(3L, r3.getId().longValue());
        assertTrue(r3.getOk());
    }

    @Test
    void todasOk() {
        DevolucionService self = mock(DevolucionService.class);
        DevolucionService service = buildService(self);

        RetiroBloqueResultadoDto res =
                service.retirarEnBloque(Arrays.asList(10L, 11L), null);

        assertEquals(2, res.getResultados().size());
        assertTrue(res.getResultados().get(0).getOk());
        assertTrue(res.getResultados().get(1).getOk());
    }

    @Test
    void listaNulaDaResultadoVacio() {
        DevolucionService service = buildService(mock(DevolucionService.class));

        RetiroBloqueResultadoDto res = service.retirarEnBloque(null, null);

        assertNotNull(res.getResultados());
        assertTrue(res.getResultados().isEmpty());
    }
}
