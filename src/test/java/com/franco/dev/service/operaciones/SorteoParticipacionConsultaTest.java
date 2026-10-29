package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SorteoParticipacionConsultaTest {

    @Test
    void codigoConSufijoDeTicketEncuentraParticipacion() {
        assertEquals("S3-2-1501", SorteoParticipacionConsulta.normalizarCodigo("S3-2-1501-2/3"));
        assertEquals("S3-2-1501", SorteoParticipacionConsulta.normalizarCodigo("S3-2-1501-1/1"));
        assertEquals("S3-2-1501", SorteoParticipacionConsulta.normalizarCodigo("  S3-2-1501-12/30 "));
    }

    @Test
    void codigoSinSufijoQuedaIgual() {
        assertEquals("S3-2-1501", SorteoParticipacionConsulta.normalizarCodigo("S3-2-1501"));
        assertEquals("S3-2", SorteoParticipacionConsulta.normalizarCodigo("S3-2"));
    }

    @Test
    void esCaseInsensitive() {
        assertEquals("S3-2-1501", SorteoParticipacionConsulta.normalizarCodigo("s3-2-1501-2/3"));
        assertEquals("S3-2-1501", SorteoParticipacionConsulta.normalizarCodigo("s3-2-1501"));
    }

    @Test
    void vacioDaNull() {
        assertNull(SorteoParticipacionConsulta.normalizarCodigo(null));
        assertNull(SorteoParticipacionConsulta.normalizarCodigo("   "));
    }

    @Test
    void detectaCodigoDeTicket() {
        assertTrue(SorteoParticipacionConsulta.esCodigoDeTicket("S3-2-1501-2/3"));
        assertFalse(SorteoParticipacionConsulta.esCodigoDeTicket("S3-2-1501"));
        assertFalse(SorteoParticipacionConsulta.esCodigoDeTicket(null));
    }

    @Test
    void escapaComodinesDeLike() {
        assertEquals("S3\\_2\\%", SorteoParticipacionConsulta.escaparLike("S3_2%"));
    }
}
