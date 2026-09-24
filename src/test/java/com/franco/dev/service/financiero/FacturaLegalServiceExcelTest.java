package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El Excel de facturas saca gravadas e IVA de los parciales. El filial ya los graba netos del
 * descuento (ParcialesCalculator): volver a restarlo los dejaba descontados dos veces (factura 32487
 * de la sucursal 24: 100.000 con 1.000 de descuento salia con IVA 10 de 9.000 en vez de 9.091).
 */
public class FacturaLegalServiceExcelTest {

    private static FacturaLegal factura(Double totalFinal, Double descuento, Double p0, Double p5, Double p10) {
        FacturaLegal f = new FacturaLegal();
        f.setTotalFinal(totalFinal);
        f.setDescuento(descuento);
        f.setTotalParcial0(p0);
        f.setTotalParcial5(p5);
        f.setTotalParcial10(p10);
        return f;
    }

    @Test
    void parcialesNetosNoSeTocan() {
        FacturaLegal f = factura(100000.0, 1000.0, 0.0, 0.0, 100000.0);
        assertEquals(1.0, FacturaLegalService.factorAlTotal(f), 1e-9);
        assertEquals(100000.0, FacturaLegalService.parcialAlTotal(f, f.getTotalParcial10()), 0.001);
        // IVA 10 de la 32487: 100.000 / 11
        assertEquals(9090.909, FacturaLegalService.parcialAlTotal(f, f.getTotalParcial10()) / 11, 0.001);
    }

    @Test
    void parcialesBrutosSeLlevanAlTotal() {
        // Bug historico del filial: total_final neto, parciales brutos.
        FacturaLegal f = factura(9000.0, 1000.0, 0.0, 4000.0, 6000.0);
        assertEquals(0.9, FacturaLegalService.factorAlTotal(f), 1e-9);
        assertEquals(3600.0, FacturaLegalService.parcialAlTotal(f, f.getTotalParcial5()), 0.001);
        assertEquals(5400.0, FacturaLegalService.parcialAlTotal(f, f.getTotalParcial10()), 0.001);
    }

    @Test
    void sinDescuentoEsUno() {
        FacturaLegal f = factura(7000.0, null, 1000.0, 2000.0, 4000.0);
        assertEquals(1.0, FacturaLegalService.factorAlTotal(f), 1e-9);
    }

    @Test
    void parcialesNulosOCeroNoDividenPorCero() {
        assertEquals(1.0, FacturaLegalService.factorAlTotal(factura(5000.0, 0.0, null, null, null)), 1e-9);
        assertEquals(1.0, FacturaLegalService.factorAlTotal(factura(5000.0, 0.0, 0.0, 0.0, 0.0)), 1e-9);
    }

    @Test
    void totalFinalNuloEsUno() {
        assertEquals(1.0, FacturaLegalService.factorAlTotal(factura(null, 500.0, 0.0, 0.0, 6000.0)), 1e-9);
    }

    @Test
    void parcialNuloEsCero() {
        FacturaLegal f = factura(6000.0, 0.0, 0.0, null, 6000.0);
        assertEquals(0.0, FacturaLegalService.parcialAlTotal(f, f.getTotalParcial5()), 0.001);
    }
}
