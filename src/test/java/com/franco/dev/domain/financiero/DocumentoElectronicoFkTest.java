package com.franco.dev.domain.financiero;

import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las relaciones de DocumentoElectronico son de solo lectura (insertable = false), asi que las FK
 * solo se persisten por las columnas planas. Antes de este cambio el INSERT que generaba Hibernate
 * no incluia factura_legal_id ni lote_de_id: crear un DE desde central fallaba contra el NOT NULL
 * y vincularDocumentosALote no vinculaba nada (spike 2026-09-17).
 */
class DocumentoElectronicoFkTest {

    @Test
    void setFacturaLegal_sincronizaLaColumnaPlana() {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(4321L);
        DocumentoElectronico de = new DocumentoElectronico();

        de.setFacturaLegal(factura);

        assertEquals(4321L, de.getFacturaLegalId());

        de.setFacturaLegal(null);
        assertNull(de.getFacturaLegalId(), "una nota no tiene factura y la columna debe quedar NULL");
    }

    @Test
    void setLoteDe_sincronizaLaColumnaPlana() {
        LoteDE lote = new LoteDE();
        lote.setId(99L);
        DocumentoElectronico de = new DocumentoElectronico();

        de.setLoteDe(lote);

        assertEquals(99L, de.getLoteDeId());

        de.setLoteDe(null);
        assertNull(de.getLoteDeId());
    }

    @Test
    void lasCuatroFkSonColumnasEscribibles() throws Exception {
        for (String campo : new String[]{"facturaLegalId", "notaCreditoId", "notaRemisionId", "loteDeId"}) {
            Field field = DocumentoElectronico.class.getDeclaredField(campo);
            Column column = field.getAnnotation(Column.class);
            assertNotNull(column, campo + " tiene que estar mapeado con @Column");
            assertTrue(column.insertable(), campo + " tiene que ser insertable");
            assertTrue(column.updatable(), campo + " tiene que ser updatable");
        }
    }
}
