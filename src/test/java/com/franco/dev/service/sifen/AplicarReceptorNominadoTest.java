package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Al aprobarse una nominación la factura toma el receptor que SIFEN tiene: antes solo recibía el cliente
 * y seguía diciendo «SIN NOMBRE» en el listado y en el filtro de innominadas (#433).
 */
class AplicarReceptorNominadoTest {

    private static FacturaLegal facturaInnominada() {
        FacturaLegal f = new FacturaLegal();
        f.setNombre("SIN NOMBRE");
        f.setRuc("X");
        f.setDireccion("CARGADA A MANO");
        f.setTotalFinal(150000.0);
        return f;
    }

    private static Cliente cliente(String nombre, String documento, boolean tributa, String direccion) {
        Persona p = new Persona();
        p.setNombre(nombre);
        p.setDocumento(documento);
        p.setDireccion(direccion);
        Cliente c = new Cliente();
        c.setPersona(p);
        c.setTributa(tributa);
        return c;
    }

    @Test
    void contribuyenteQuedaConRucSinDigitoVerificador() {
        FacturaLegal f = facturaInnominada();
        Cliente c = cliente("AVOX S.A", "80061439", true, "AVDA. ESPAÑA 123");

        SifenEventoService.aplicarReceptorNominado(f, c);

        assertSame(c, f.getCliente());
        assertEquals("AVOX S.A", f.getNombre());
        assertEquals("80061439", f.getRuc());
        assertEquals("AVDA. ESPAÑA 123", f.getDireccion());
    }

    @Test
    void noContribuyenteQuedaConSuDocumentoYNombreEnMayusculas() {
        FacturaLegal f = facturaInnominada();
        Cliente c = cliente("Mauro Rivas", "5466642", false, "  ");

        SifenEventoService.aplicarReceptorNominado(f, c);

        assertEquals("MAURO RIVAS", f.getNombre());
        assertEquals("5466642", f.getRuc());
        assertEquals("CARGADA A MANO", f.getDireccion(), "una dirección en blanco no pisa la de la factura");
    }
}
