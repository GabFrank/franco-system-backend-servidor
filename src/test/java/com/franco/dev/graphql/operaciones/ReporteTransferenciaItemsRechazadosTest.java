package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoRechazo;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.impresion.ProductoFaltanteReporteItem;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.productos.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/** Reporte PDF de productos faltantes: filas, cabecera, vacio y truncado. */
class ReporteTransferenciaItemsRechazadosTest {

    private TransferenciaItemService service;
    private ImpresionService impresion;
    private SucursalService sucursalService;
    private TesoreriaSecurityService seg;
    private TransferenciaItemGraphQL resolver;

    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<List<ProductoFaltanteReporteItem>> filas = ArgumentCaptor.forClass(List.class);
    @SuppressWarnings("unchecked")
    private final ArgumentCaptor<Map<String, Object>> cabecera = ArgumentCaptor.forClass(Map.class);

    @BeforeEach
    void preparar() {
        service = mock(TransferenciaItemService.class);
        impresion = mock(ImpresionService.class);
        sucursalService = mock(SucursalService.class);
        seg = mock(TesoreriaSecurityService.class);
        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "seg", seg);
        ReflectionTestUtils.setField(resolver, "impresionService", impresion);
        ReflectionTestUtils.setField(resolver, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(resolver, "productoService", mock(ProductoService.class));
        when(impresion.imprimirProductosFaltantesTransferencia(anyList(), anyMap())).thenReturn("PDF");
    }

    private static Usuario usuario(String nombre) {
        Persona p = new Persona();
        p.setNombre(nombre);
        Usuario u = new Usuario();
        u.setPersona(p);
        return u;
    }

    private static TransferenciaItem item() {
        Sucursal origen = new Sucursal();
        origen.setNombre("DEPOSITO");
        Sucursal destino = new Sucursal();
        destino.setNombre("SUC 1");
        Transferencia t = new Transferencia();
        t.setId(58147L);
        t.setCreadoEn(LocalDateTime.of(2026, 9, 23, 10, 9));
        t.setSucursalOrigen(origen);
        t.setSucursalDestino(destino);
        t.setEstado(TransferenciaEstado.EN_TRANSITO);
        t.setUsuarioPreparacion(usuario("PREPARADOR"));
        t.setUsuarioTransporte(usuario("CHOFER"));
        Producto producto = new Producto();
        producto.setDescripcion("NIKO NARANJA 1L");
        Presentacion pres = new Presentacion();
        pres.setProducto(producto);
        TransferenciaItem ti = new TransferenciaItem();
        ti.setTransferencia(t);
        ti.setPresentacionPreTransferencia(pres);
        ti.setCantidadPreTransferencia(2.0);
        ti.setCantidadPreparacion(1.0);
        return ti;
    }

    @Test
    void laEtapaEsLaPrimeraConElMotivoFiltradoYSuResponsable() {
        TransferenciaItem ti = item();
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        ti.setMotivoRechazoTransporte(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        ProductoFaltanteReporteItem r = ProductoFaltanteReporteItem.de(ti, TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        assertEquals("Preparación (origen)", r.getEtapa());
        assertEquals("PREPARADOR", r.getResponsable());
        assertEquals("23/09/2026 10:09", r.getFecha());
        assertEquals("EN TRANSITO", r.getEstado());
        assertEquals("DEPOSITO", r.getOrigen());
        assertEquals("NIKO NARANJA 1L", r.getProducto());
    }

    @Test
    void siElDestinoCambioElMotivoLaEtapaEsLaDelMotivoFiltrado() {
        TransferenciaItem ti = item();
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        ti.setMotivoRechazoTransporte(TransferenciaItemMotivoRechazo.PRODUCTO_AVERIADO);
        ProductoFaltanteReporteItem r = ProductoFaltanteReporteItem.de(ti, TransferenciaItemMotivoRechazo.PRODUCTO_AVERIADO);
        assertEquals("Transporte", r.getEtapa());
        assertEquals("CHOFER", r.getResponsable());
    }

    @Test
    void sinResultadosDevuelveNullYNoArmaElPdf() {
        when(service.findRechazados(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        assertNull(resolver.reporteTransferenciaItemsRechazados(null, null, null, null, null, "2026-10-01", "2026-10-31"));
        verify(impresion, never()).imprimirProductosFaltantesTransferencia(anyList(), anyMap());
    }

    @Test
    void cabeceraConPeriodoYFiltros() {
        TransferenciaItem ti = item();
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        when(service.findRechazados(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(ti)));
        Sucursal s = new Sucursal();
        s.setNombre("DEPOSITO");
        when(sucursalService.findById(13L)).thenReturn(Optional.of(s));
        when(seg.currentUsuario()).thenReturn(usuario("MAURO"));

        assertEquals("PDF", resolver.reporteTransferenciaItemsRechazados(null, 13L, null, null, null, "2026-10-01", "2026-10-31"));
        verify(impresion).imprimirProductosFaltantesTransferencia(filas.capture(), cabecera.capture());
        assertEquals(1, filas.getValue().size());
        assertEquals("01/10/2026 al 31/10/2026", cabecera.getValue().get("periodo"));
        assertEquals("FALTA PRODUCTO", cabecera.getValue().get("motivo"));
        assertEquals("DEPOSITO", cabecera.getValue().get("origen"));
        assertEquals("TODAS", cabecera.getValue().get("destino"));
        assertEquals("TODOS", cabecera.getValue().get("filtroProducto"));
        assertEquals("MAURO", cabecera.getValue().get("usuario"));
        assertEquals("", cabecera.getValue().get("aviso"));
    }

    @Test
    void conNumeroDeTransferenciaElPeriodoEsLaTransferenciaYNoHayFechas() {
        TransferenciaItem ti = item();
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        when(service.findRechazados(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(ti)));
        resolver.reporteTransferenciaItemsRechazados(null, null, null, null, 58147L, "2020-01-01", "2026-01-01");
        verify(service).findRechazados(any(), isNull(), isNull(), isNull(), eq(58147L), isNull(), isNull(), any());
        verify(impresion).imprimirProductosFaltantesTransferencia(anyList(), cabecera.capture());
        assertEquals("Transferencia N° 58147", cabecera.getValue().get("periodo"));
    }

    @Test
    void masDelTopeAvisaQueSeTrunco() {
        TransferenciaItem ti = item();
        ti.setMotivoRechazoPreparacion(TransferenciaItemMotivoRechazo.FALTA_PRODUCTO);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(service.findRechazados(any(), any(), any(), any(), any(), any(), any(), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(Collections.singletonList(ti), PageRequest.of(0, 5000), 7499));
        resolver.reporteTransferenciaItemsRechazados(null, null, null, null, null, "2025-01-01", "2025-12-31");
        assertEquals(5000, pageable.getValue().getPageSize());
        verify(impresion).imprimirProductosFaltantesTransferencia(anyList(), cabecera.capture());
        assertTrue(((String) cabecera.getValue().get("aviso")).contains("5000 de 7499"));
    }
}
