package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.domain.operaciones.dto.RetiroBloqueResultadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroDevolucionResultadoDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test de la logica de retiro en bloque (retirarEnBloque).
 *
 * Contrato clave: el fallo de una devolucion (ej. stock insuficiente) NO aborta
 * el resto; cada una se reporta por id (ok / mensaje). Ademas, retirarEnBloque
 * valida que todas sean del mismo proveedor y crea una cabecera RetiroDevolucion.
 *
 * Se mockean el repository (findById devuelve devoluciones con el mismo proveedor) y el
 * RetiroDevolucionService (crea la cabecera). Cada linea corre el retirarLinea real dentro del
 * TransactionTemplate del service; una linea falla cuando su estado no admite RETIRADO.
 */
class DevolucionRetiroEnBloqueTest {

    private Proveedor proveedor(long id) {
        Proveedor p = new Proveedor();
        p.setId(id);
        return p;
    }

    private Devolucion devConProveedor(long id, Proveedor p) {
        return devConProveedor(id, p, DevolucionEstado.SEPARADO);
    }

    private Devolucion devConProveedor(long id, Proveedor p, DevolucionEstado estado) {
        Devolucion d = new Devolucion();
        d.setId(id);
        d.setProveedor(p);
        d.setTipo(TipoDevolucion.CON_PROVEEDOR);
        d.setEstado(estado);
        return d;
    }

    private DevolucionService buildService(DevolucionRepository repository,
                                           RetiroDevolucionService retiroService) {
        return new DevolucionServiceDePrueba().con(repository).con(retiroService).build();
    }

    @Test
    void unaFallaNoAbortaLasDemas() {
        Proveedor p = proveedor(1L);
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findById(1L)).thenReturn(Optional.of(devConProveedor(1L, p)));
        // La 2 sigue PENDIENTE: RETIRADO solo desde SEPARADO o COLECTADO, asi que su linea falla.
        when(repo.findById(2L)).thenReturn(Optional.of(devConProveedor(2L, p, DevolucionEstado.PENDIENTE)));
        when(repo.findById(3L)).thenReturn(Optional.of(devConProveedor(3L, p)));

        RetiroDevolucionService retiroService = mock(RetiroDevolucionService.class);
        RetiroDevolucion header = new RetiroDevolucion();
        header.setId(99L);
        when(retiroService.crear(any(), any())).thenReturn(header);

        DevolucionService service = buildService(repo, retiroService);

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
        assertEquals("RETIRADO solo desde SEPARADO o COLECTADO", r2.getMensaje());

        assertEquals(3L, r3.getId().longValue());
        assertTrue(r3.getOk());
    }

    @Test
    void todasOk() {
        Proveedor p = proveedor(5L);
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findById(anyLong())).thenAnswer(inv -> Optional.of(devConProveedor(inv.getArgument(0), p)));

        RetiroDevolucionService retiroService = mock(RetiroDevolucionService.class);
        RetiroDevolucion header = new RetiroDevolucion();
        header.setId(1L);
        when(retiroService.crear(any(), any())).thenReturn(header);

        DevolucionService service = buildService(repo, retiroService);

        RetiroBloqueResultadoDto res =
                service.retirarEnBloque(Arrays.asList(10L, 11L), null);

        assertEquals(2, res.getResultados().size());
        assertTrue(res.getResultados().get(0).getOk());
        assertTrue(res.getResultados().get(1).getOk());
    }

    @Test
    void proveedoresDistintosFalla() {
        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(devConProveedor(1L, proveedor(1L))));
        when(repo.findById(2L)).thenReturn(Optional.of(devConProveedor(2L, proveedor(2L))));

        DevolucionService service = buildService(repo, mock(RetiroDevolucionService.class));

        try {
            service.retirarEnBloque(Arrays.asList(1L, 2L), null);
            org.junit.jupiter.api.Assertions.fail("Debia rechazar proveedores distintos");
        } catch (GraphQLException e) {
            assertTrue(e.getMessage().contains("unico proveedor"));
        }
    }

    @Test
    void listaNulaDaResultadoVacio() {
        DevolucionService service = buildService(null, null);

        RetiroBloqueResultadoDto res = service.retirarEnBloque(null, null);

        assertNotNull(res.getResultados());
        assertTrue(res.getResultados().isEmpty());
    }
}
