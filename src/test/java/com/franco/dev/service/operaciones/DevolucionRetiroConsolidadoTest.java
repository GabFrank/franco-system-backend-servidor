package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.dto.RetiroDevolucionResumenDto;
import com.franco.dev.domain.operaciones.dto.RetiroProveedorConsolidadoDto;
import com.franco.dev.domain.operaciones.dto.RetiroSucursalGrupoDto;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.DevolucionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El consolidado de retiro trae cada devolucion con sus productos: es lo que
 * la PWA muestra al escanear el QR de la etiqueta (uno por devolucion).
 */
class DevolucionRetiroConsolidadoTest {

    private Devolucion dev(long id, Sucursal suc, DevolucionEstado estado) {
        Devolucion d = new Devolucion();
        d.setId(id);
        d.setIdentificador("DEV-" + suc.getId() + "-" + id);
        d.setSucursalOrigen(suc);
        d.setEstado(estado);
        return d;
    }

    private DevolucionItem item(long productoId, double cantidad) {
        Producto p = new Producto();
        p.setId(productoId);
        p.setDescripcion("PRODUCTO " + productoId);
        Presentacion pres = new Presentacion();
        pres.setCantidad(12.0);
        DevolucionItem it = new DevolucionItem();
        it.setProducto(p);
        it.setPresentacion(pres);
        it.setCantidad(cantidad);
        return it;
    }

    @Test
    void cadaDevolucionTraeSusProductos() {
        Sucursal suc = new Sucursal();
        suc.setId(1L);
        suc.setNombre("CENTRAL");
        Devolucion d24 = dev(24, suc, DevolucionEstado.SEPARADO);
        Devolucion d25 = dev(25, suc, DevolucionEstado.COLECTADO);

        DevolucionRepository repo = mock(DevolucionRepository.class);
        when(repo.findByProveedorIdAndEstados(eq(7L), any()))
                .thenReturn(new ArrayList<>(Arrays.asList(d24, d25)));
        DevolucionItemService itemService = mock(DevolucionItemService.class);
        when(itemService.findByDevolucionId(24L)).thenReturn(Arrays.asList(item(1, 4), item(2, 9), item(3, 7)));
        when(itemService.findByDevolucionId(25L)).thenReturn(Arrays.asList(item(1, 2)));

        DevolucionService service = new DevolucionService(repo, itemService, null, null, null,
                null, null, null, null, null);
        RetiroProveedorConsolidadoDto res = service.getRetiroConsolidado(7L, null);

        assertEquals(1, res.getGrupos().size());
        RetiroSucursalGrupoDto grupo = res.getGrupos().get(0);
        List<RetiroDevolucionResumenDto> devs = grupo.getDevoluciones();
        assertEquals(2, devs.size());
        assertEquals("DEV-1-24", devs.get(0).getIdentificador());
        assertEquals("SEPARADO", devs.get(0).getEstado());
        assertEquals(3, devs.get(0).getProductos().size());
        assertEquals(9.0, devs.get(0).getProductos().get(1).getCantidad());
        assertEquals("x12", devs.get(0).getProductos().get(1).getPresentacion());
        assertEquals("DEV-1-25", devs.get(1).getIdentificador());
        assertEquals(1, devs.get(1).getProductos().size());
        // El consolidado por producto sigue sumando entre devoluciones.
        assertEquals(6.0, grupo.getLineas().get(0).getCantidadTotal());
    }
}
