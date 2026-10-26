package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.MotivoAveria;
import com.franco.dev.domain.operaciones.dto.EtiquetaDevolucionDto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La etiqueta de separado es una sola por devolucion, con un QR y todos sus
 * productos. Antes salia una por item, todas con el mismo QR.
 */
class EtiquetaDevolucionServiceTest {

    private Devolucion devolucion() {
        Sucursal suc = new Sucursal();
        suc.setId(1L);
        suc.setNombre("CENTRAL");
        Devolucion d = new Devolucion();
        d.setId(24L);
        d.setIdentificador("DEV-1-24");
        d.setSucursalOrigen(suc);
        d.setFecha(LocalDateTime.of(2026, 10, 26, 9, 30));
        return d;
    }

    private DevolucionItem item(long productoId, String descripcion, double cantidad) {
        Producto p = new Producto();
        p.setId(productoId);
        p.setDescripcion(descripcion);
        Presentacion pres = new Presentacion();
        pres.setId(productoId * 10);
        pres.setDescripcion("unidad");
        pres.setCantidad(1.0);
        MotivoAveria motivo = new MotivoAveria();
        motivo.setDescripcion("VENCIDO");
        DevolucionItem it = new DevolucionItem();
        it.setProducto(p);
        it.setPresentacion(pres);
        it.setCantidad(cantidad);
        it.setMotivoAveria(motivo);
        return it;
    }

    private List<DevolucionItem> sieteItems() {
        List<DevolucionItem> items = new ArrayList<>();
        for (int i = 1; i <= 7; i++) items.add(item(i, "PRODUCTO " + i, i));
        return items;
    }

    @Test
    void unaEtiquetaConTodosLosProductosYElQrDeLaDevolucion() {
        EtiquetaDevolucionDto e = EtiquetaDevolucionService.armar(devolucion(), sieteItems(),
                pres -> "COD" + pres.getId());

        assertEquals("DEV-1-24", e.getIdentificador());
        assertEquals("DEV-1-24", e.getQr());
        assertEquals("CENTRAL", e.getSucursal());
        assertEquals("Sin proveedor", e.getProveedor());
        assertEquals("26/10/2026", e.getFecha());
        assertEquals(7, e.getProductos().size());
        assertEquals("COD30", e.getProductos().get(2).getCodigo());
        assertEquals("3 · PRODUCTO 3", e.getProductos().get(2).getProducto());
        assertEquals("VENCIDO", e.getProductos().get(2).getMotivo());
    }

    @Test
    void sinItemsNoHayEtiqueta() {
        assertThrows(GraphQLException.class, () ->
                EtiquetaDevolucionService.armar(devolucion(), Collections.emptyList(), pres -> ""));
    }

    @Test
    void elTotalDiceCuantosProductosTrae() {
        assertEquals("1 producto", EtiquetaDevolucionService.textoTotal(1));
        assertEquals("7 productos", EtiquetaDevolucionService.textoTotal(7));
    }

    /** El PDF de una devolucion de 7 productos es una sola pagina, no 7 tarjetas. */
    @Test
    void elPdfEsUnaSolaPagina() throws Exception {
        EtiquetaDevolucionDto e = EtiquetaDevolucionService.armar(devolucion(), sieteItems(), pres -> "");
        JasperReport report = JasperCompileManager.compileReport(
                new ClassPathResource("reports/etiquetas-devolucion.jrxml").getInputStream());
        Map<String, Object> params = new HashMap<>();
        params.put("identificador", e.getIdentificador());
        params.put("sucursal", e.getSucursal());
        params.put("proveedor", e.getProveedor());
        params.put("fecha", e.getFecha());
        params.put("qr", e.getQr());
        params.put("cantidadProductos", e.getProductos().size());
        JasperPrint print = JasperFillManager.fillReport(report, params,
                new JRBeanCollectionDataSource(e.getProductos()));
        assertEquals(1, print.getPages().size());
    }
}
