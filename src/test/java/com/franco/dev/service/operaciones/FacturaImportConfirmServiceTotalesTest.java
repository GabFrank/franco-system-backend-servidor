package com.franco.dev.service.operaciones;

import com.franco.dev.graphql.operaciones.dto.FacturaImportPreviewDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del guardrail anti-perdida-silenciosa de items: control de cuadre entre la suma de
 * los items leidos y el totalGeneral declarado por la factura.
 */
class FacturaImportConfirmServiceTotalesTest {

    private FacturaImportConfirmService service;

    @BeforeEach
    void setUp() {
        // aplicarControlDeTotales es puro (no usa dependencias inyectadas)
        service = new FacturaImportConfirmService();
    }

    private FacturaImportPreviewDto.ItemPreview item(String total) {
        FacturaImportPreviewDto.ItemPreview ip = new FacturaImportPreviewDto.ItemPreview();
        ip.setTotalItem(total != null ? new BigDecimal(total) : null);
        return ip;
    }

    @Test
    void totalesCuadran_sumaIgualAlTotal() {
        FacturaImportPreviewDto dto = new FacturaImportPreviewDto();
        List<FacturaImportPreviewDto.ItemPreview> items = Arrays.asList(item("100000"), item("50000"));

        service.aplicarControlDeTotales(dto, items, new BigDecimal("150000"));

        assertEquals(Boolean.TRUE, dto.getTotalesCuadran());
        assertEquals(new BigDecimal("150000"), dto.getSumaItems());
        assertNull(dto.getTotalesAdvertencia());
    }

    @Test
    void totalesNoCuadran_faltaUnItem_marcaAdvertencia() {
        FacturaImportPreviewDto dto = new FacturaImportPreviewDto();
        // Solo se leyo la pagina 1 (un item) pero la factura declara mas
        List<FacturaImportPreviewDto.ItemPreview> items = Collections.singletonList(item("100000"));

        service.aplicarControlDeTotales(dto, items, new BigDecimal("150000"));

        assertEquals(Boolean.FALSE, dto.getTotalesCuadran());
        assertNotNull(dto.getTotalesAdvertencia());
        assertTrue(dto.getTotalesAdvertencia().toLowerCase().contains("pagina"));
    }

    @Test
    void totalesCuadran_dentroDeTolerancia1porciento() {
        FacturaImportPreviewDto dto = new FacturaImportPreviewDto();
        // diferencia de 500 sobre 150000 = 0.33% < 1% -> cuadra
        List<FacturaImportPreviewDto.ItemPreview> items = Arrays.asList(item("100000"), item("49500"));

        service.aplicarControlDeTotales(dto, items, new BigDecimal("150000"));

        assertEquals(Boolean.TRUE, dto.getTotalesCuadran());
    }

    @Test
    void sinTotalGeneral_noComparable_pideRevisionManual() {
        FacturaImportPreviewDto dto = new FacturaImportPreviewDto();
        List<FacturaImportPreviewDto.ItemPreview> items = Collections.singletonList(item("100000"));

        service.aplicarControlDeTotales(dto, items, null);

        assertNull(dto.getTotalesCuadran());
        assertNotNull(dto.getTotalesAdvertencia());
        assertEquals(new BigDecimal("100000"), dto.getSumaItems());
    }

    @Test
    void sinItemsLegibles_noComparable() {
        FacturaImportPreviewDto dto = new FacturaImportPreviewDto();
        List<FacturaImportPreviewDto.ItemPreview> items = Collections.singletonList(item(null));

        service.aplicarControlDeTotales(dto, items, new BigDecimal("150000"));

        assertNull(dto.getTotalesCuadran());
        assertNull(dto.getSumaItems());
        assertNotNull(dto.getTotalesAdvertencia());
    }
}
