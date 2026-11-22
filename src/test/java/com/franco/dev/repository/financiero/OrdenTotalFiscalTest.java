package com.franco.dev.repository.financiero;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tanda de documentos fiscales de #674. Facturas, documentos electronicos, lotes DE y notas tienen
 * PK (id, sucursal_id) y el id se repite entre sucursales (179.347 facturas en bodega3): el orden
 * termina en id + sucursal. El de facturaLegales esta en FacturaLegalListaOrdenTest. Corre en CI.
 */
class OrdenTotalFiscalTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "FacturaLegalRepository           | findByCreadoEnBetweenAndSucursalId | order by fl.creadoEn, fl.id, fl.sucursalId",
            "FacturaLegalRepository           | buscarCandidatasANotaCredito       | ORDER BY f.numeroFactura DESC, f.id DESC, f.sucursalId DESC",
            "DocumentoElectronicoRepository   | findByFilters                      | ORDER BY d.id DESC, d.sucursalId DESC",
            "LoteDERepository                 | findByEstadoOrFechaProcesadoBetween | ORDER BY l.id ASC, l.sucursalId ASC",
            "NotaCreditoRepository            | findByFilters                      | ORDER BY n.fecha DESC, n.id DESC, n.sucursalId DESC",
            "NotaRemisionRepository           | findByFilters                      | ORDER BY n.fecha DESC, n.id DESC, n.sucursalId DESC",
            "EventoInutilizacionDERepository  | findWithFilters                    | ORDER BY e.creadoEn DESC, e.id DESC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName("com.franco.dev.repository.financiero." + repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertFalse(consultas.isEmpty(), metodo);
        consultas.forEach(q -> assertTrue(q.endsWith(final_), metodo + ": " + q));
    }
}
