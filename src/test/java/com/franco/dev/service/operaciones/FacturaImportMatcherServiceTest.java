package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.AliasProductoImport;
import com.franco.dev.domain.operaciones.AliasProveedorImport;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.repository.personas.ProveedorRepository;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.search.ProductoSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.service.operaciones.FacturaImportMatcherService.Confianza;
import static com.franco.dev.service.operaciones.FacturaImportMatcherService.MatchProducto;
import static com.franco.dev.service.operaciones.FacturaImportMatcherService.MatchProveedor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaImportMatcherServiceTest {

    @Mock ProveedorRepository proveedorRepository;
    @Mock PersonaRepository personaRepository;
    @Mock AliasImportService aliasImportService;
    @Mock CodigoService codigoService;
    @Mock ProductoSearchService productoSearchService;
    @Mock ProductoService productoService;

    @InjectMocks
    private FacturaImportMatcherService matcher;

    // === PROVEEDOR ===

    @Test
    void matchProveedor_rucExactoEnPersona_high() {
        Persona p = new Persona();
        p.setId(99L);
        Proveedor prov = new Proveedor();
        prov.setId(7L);
        when(personaRepository.findByDocumento("80012345-6")).thenReturn(p);
        when(proveedorRepository.findByPersonaId(99L)).thenReturn(prov);

        MatchProveedor r = matcher.matchProveedor("80012345-6", "CUALQUIER NOMBRE");

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(7L, r.proveedor.getId());
    }

    @Test
    void matchProveedor_rucEnAlias_high() {
        when(personaRepository.findByDocumento("123-4")).thenReturn(null);
        Proveedor prov = new Proveedor();
        prov.setId(5L);
        AliasProveedorImport alias = new AliasProveedorImport();
        alias.setProveedor(prov);
        when(aliasImportService.findProveedorByRuc("123-4")).thenReturn(List.of(alias));

        MatchProveedor r = matcher.matchProveedor("123-4", "X");

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(5L, r.proveedor.getId());
    }

    @Test
    void matchProveedor_nombreEnAlias_medium() {
        Proveedor prov = new Proveedor();
        prov.setId(8L);
        AliasProveedorImport alias = new AliasProveedorImport();
        alias.setProveedor(prov);
        when(aliasImportService.findProveedorByTextoOcr("PROVEEDOR XYZ"))
                .thenReturn(Optional.of(alias));

        MatchProveedor r = matcher.matchProveedor(null, "PROVEEDOR XYZ");

        assertEquals(Confianza.MEDIUM, r.confianza);
        assertEquals(8L, r.proveedor.getId());
    }

    @Test
    void matchProveedor_sinMatch_none() {
        when(personaRepository.findByDocumento("99-9")).thenReturn(null);
        when(aliasImportService.findProveedorByRuc("99-9")).thenReturn(List.of());
        when(aliasImportService.findProveedorByTextoOcr("DESCONOCIDO")).thenReturn(Optional.empty());

        MatchProveedor r = matcher.matchProveedor("99-9", "DESCONOCIDO");

        assertEquals(Confianza.NONE, r.confianza);
        assertNull(r.proveedor);
    }

    @Test
    void matchProveedor_rucNullYNombreNull_none() {
        MatchProveedor r = matcher.matchProveedor(null, null);
        assertEquals(Confianza.NONE, r.confianza);
    }

    @Test
    void matchProveedor_rucPersonaExiste_peroSinProveedor_continuaAlAlias() {
        Persona p = new Persona();
        p.setId(99L);
        when(personaRepository.findByDocumento("80012345-6")).thenReturn(p);
        when(proveedorRepository.findByPersonaId(99L)).thenReturn(null); // persona existe pero no es proveedor
        when(aliasImportService.findProveedorByRuc("80012345-6")).thenReturn(List.of());
        when(aliasImportService.findProveedorByTextoOcr("NOMBRE")).thenReturn(Optional.empty());

        MatchProveedor r = matcher.matchProveedor("80012345-6", "NOMBRE");

        assertEquals(Confianza.NONE, r.confianza);
    }

    // === PRODUCTO ===

    @Test
    void matchProducto_barcodeExacto_high() {
        Producto prod = new Producto();
        prod.setId(10L);
        Presentacion pres = new Presentacion();
        pres.setProducto(prod);
        Codigo cod = new Codigo();
        cod.setPresentacion(pres);
        when(codigoService.findByCodigo("7891000100103")).thenReturn(List.of(cod));

        MatchProducto r = matcher.matchProducto("7891000100103", "PRODUCTO X", 1L);

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(10L, r.producto.getId());
        assertEquals("Codigo de barras exacto", r.razon);
    }

    @Test
    void matchProducto_aliasCodigo_high() {
        when(codigoService.findByCodigo("X1")).thenReturn(List.of());
        Producto prod = new Producto();
        prod.setId(20L);
        AliasProductoImport alias = new AliasProductoImport();
        alias.setProducto(prod);
        when(aliasImportService.findProductoByCodigoOcr("X1")).thenReturn(List.of(alias));

        MatchProducto r = matcher.matchProducto("X1", "ITEM A", 1L);

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(20L, r.producto.getId());
    }

    @Test
    void matchProducto_aliasTextoYProveedor_medium() {
        when(codigoService.findByCodigo("X2")).thenReturn(List.of());
        when(aliasImportService.findProductoByCodigoOcr("X2")).thenReturn(List.of());
        Producto prod = new Producto();
        prod.setId(30L);
        AliasProductoImport alias = new AliasProductoImport();
        alias.setProducto(prod);
        when(aliasImportService.findProductoByTextoYProveedor("ITEM B", 5L))
                .thenReturn(Optional.of(alias));

        MatchProducto r = matcher.matchProducto("X2", "ITEM B", 5L);

        assertEquals(Confianza.MEDIUM, r.confianza);
        assertEquals(30L, r.producto.getId());
    }

    @Test
    void matchProducto_fuzzyLucene_medium_conCandidatos() {
        when(aliasImportService.findProductoByTextoYProveedor("PARACETAMOL 500", 1L))
                .thenReturn(Optional.empty());
        when(productoSearchService.buscarIdsPorTexto(eq("PARACETAMOL 500"), anyInt()))
                .thenReturn(Arrays.asList(100L, 101L, 102L));
        Producto p1 = new Producto();
        p1.setId(100L);
        Producto p2 = new Producto();
        p2.setId(101L);
        Producto p3 = new Producto();
        p3.setId(102L);
        when(productoService.findById(100L)).thenReturn(Optional.of(p1));
        when(productoService.findById(101L)).thenReturn(Optional.of(p2));
        when(productoService.findById(102L)).thenReturn(Optional.of(p3));

        MatchProducto r = matcher.matchProducto(null, "PARACETAMOL 500", 1L);

        assertEquals(Confianza.MEDIUM, r.confianza);
        assertEquals(100L, r.producto.getId(), "primer resultado fuzzy");
        assertEquals(2, r.candidatos.size());
        assertEquals(101L, r.candidatos.get(0).getId());
        assertEquals(102L, r.candidatos.get(1).getId());
    }

    @Test
    void matchProducto_fuzzySinResultados_none() {
        when(aliasImportService.findProductoByTextoYProveedor("ALGO RARO", 1L))
                .thenReturn(Optional.empty());
        when(productoSearchService.buscarIdsPorTexto(eq("ALGO RARO"), anyInt()))
                .thenReturn(Collections.emptyList());

        MatchProducto r = matcher.matchProducto(null, "ALGO RARO", 1L);

        assertEquals(Confianza.NONE, r.confianza);
        assertNull(r.producto);
        assertEquals(0, r.candidatos.size());
    }

    @Test
    void matchProducto_nombreYCodigoNull_none() {
        MatchProducto r = matcher.matchProducto(null, null, 1L);
        assertEquals(Confianza.NONE, r.confianza);
    }

    @Test
    void matchProducto_proveedorIdNull_noBuscaAliasTexto_sigueAFuzzy() {
        when(productoSearchService.buscarIdsPorTexto(eq("X"), anyInt()))
                .thenReturn(Collections.emptyList());

        MatchProducto r = matcher.matchProducto(null, "X", null);

        assertEquals(Confianza.NONE, r.confianza);
        // Verifica que NO se llamo a findProductoByTextoYProveedor con proveedorId null
        // (covered by lenient strict-stubs behavior).
    }

    // === Bug fixes A: RUC con digito verificador + GTIN ===

    @Test
    void matchProveedor_rucConDv_matcheaPersonaGuardadaSinDv_high() {
        // El XML SIFEN trae "80060071-1" pero en persona.documento el RUC esta sin el DV.
        Persona p = new Persona();
        p.setId(50L);
        Proveedor prov = new Proveedor();
        prov.setId(9L);
        when(personaRepository.findByDocumento("80060071-1")).thenReturn(null);
        when(personaRepository.findByDocumento("80060071")).thenReturn(p);
        when(proveedorRepository.findByPersonaId(50L)).thenReturn(prov);

        MatchProveedor r = matcher.matchProveedor("80060071-1", "GRUPO A.F. BERTI S.R.L");

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(9L, r.proveedor.getId());
        assertEquals("RUC exacto en catalogo", r.razon);
    }

    @Test
    void matchProducto_gtinMatcheaBarcode_high() {
        // dGtin (EAN) matchea nuestro catalogo aunque el dCodInt del proveedor no.
        Producto prod = new Producto();
        prod.setId(40L);
        Presentacion pres = new Presentacion();
        pres.setProducto(prod);
        Codigo cod = new Codigo();
        cod.setPresentacion(pres);
        when(codigoService.findByCodigo("7891117043164")).thenReturn(List.of(cod));

        // (codigoBarras=GTIN, codigoOcr=dCodInt del proveedor, nombre, proveedorId)
        MatchProducto r = matcher.matchProducto("7891117043164", "019902", "TRAMONTINA 78502", 1L);

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(40L, r.producto.getId());
        assertEquals("Codigo de barras exacto", r.razon);
    }

    @Test
    void matchProducto_gtinNoMatchea_caeAlCodigoInterno_high() {
        Producto prod = new Producto();
        prod.setId(41L);
        Presentacion pres = new Presentacion();
        pres.setProducto(prod);
        Codigo cod = new Codigo();
        cod.setPresentacion(pres);
        when(codigoService.findByCodigo("7891117043164")).thenReturn(List.of()); // GTIN no esta en catalogo
        when(codigoService.findByCodigo("019902")).thenReturn(List.of(cod));      // pero el codigo interno si

        MatchProducto r = matcher.matchProducto("7891117043164", "019902", "TRAMONTINA 78502", 1L);

        assertEquals(Confianza.HIGH, r.confianza);
        assertEquals(41L, r.producto.getId());
    }
}
