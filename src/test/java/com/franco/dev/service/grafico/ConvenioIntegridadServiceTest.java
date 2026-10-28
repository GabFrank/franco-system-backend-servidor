package com.franco.dev.service.grafico;

import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.graphql.grafico.dto.ConvenioSucursalIntegridad;
import com.franco.dev.service.grafico.ConvenioIntegridadService.Fila;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConvenioIntegridadServiceTest {

    private static final long SUC = 24L;
    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 1, 10, 0);

    private static Fila fila(long id, String estado) {
        return new Fila(id, SUC, estado, T.plusMinutes(id));
    }

    private static ConvenioSucursalIntegridad comparar(List<Fila> central, List<Fila> filial, EstadoVentaCredito estado) {
        return ConvenioIntegridadService.comparar(SUC, "KM2", central, filial, estado);
    }

    @Test
    void igualesEsOk() {
        List<Fila> filas = Arrays.asList(fila(1, "ABIERTO"), fila(2, "CANCELADO"), fila(3, "FINALIZADO"), fila(4, "EN_MORA"));
        ConvenioSucursalIntegridad r = comparar(filas, filas, null);
        assertEquals(ConvenioSucursalIntegridad.OK, r.getEstadoIntegridad());
        assertEquals(4, r.getCentral().getTotal());
        assertEquals(1, r.getCentral().getAbiertas());
        assertEquals(1, r.getCentral().getCanceladas());
        assertEquals(1, r.getCentral().getFinalizadas());
        assertEquals(1, r.getCentral().getOtras());
        assertEquals(r.getCentral(), r.getFilial());
        assertEquals("2026-10-01 10:04:00", r.getUltimoCentral());
    }

    @Test
    void detectaFaltantesSobrantesYEstadoDistinto() {
        List<Fila> central = Arrays.asList(fila(1, "ABIERTO"), fila(2, "CANCELADO"), fila(9, "ABIERTO"));
        List<Fila> filial = Arrays.asList(fila(1, "ABIERTO"), fila(2, "ABIERTO"), fila(3, "ABIERTO"));
        ConvenioSucursalIntegridad r = comparar(central, filial, null);
        assertEquals(ConvenioSucursalIntegridad.DIFERENCIA, r.getEstadoIntegridad());
        assertEquals(Collections.singletonList(3L), r.getIdsFaltanEnCentral());
        assertEquals(Collections.singletonList(9L), r.getIdsSobranEnCentral());
        assertEquals(Collections.singletonList(2L), r.getIdsEstadoDistinto());
        assertEquals(1, r.getFaltanEnCentral());
    }

    @Test
    void filtroDeEstadoSoloCambiaConteosNoLaComparacion() {
        // ABIERTO en la filial, CANCELADO en el central: con filtro ABIERTO no tiene que verse como faltante.
        List<Fila> central = Arrays.asList(fila(1, "CANCELADO"), fila(2, "ABIERTO"));
        List<Fila> filial = Arrays.asList(fila(1, "ABIERTO"), fila(2, "ABIERTO"));
        ConvenioSucursalIntegridad r = comparar(central, filial, EstadoVentaCredito.ABIERTO);
        assertEquals(1, r.getCentral().getTotal());
        assertEquals(2, r.getFilial().getTotal());
        assertEquals(0, r.getFaltanEnCentral());
        assertEquals(Collections.singletonList(1L), r.getIdsEstadoDistinto());
    }

    @Test
    void ignoraFilasDeOtraSucursalConElMismoId() {
        List<Fila> central = Collections.singletonList(fila(1, "ABIERTO"));
        List<Fila> filial = Arrays.asList(fila(1, "ABIERTO"), new Fila(7, 13L, "ABIERTO", T), new Fila(1, 13L, "CANCELADO", T));
        ConvenioSucursalIntegridad r = comparar(central, filial, null);
        assertEquals(ConvenioSucursalIntegridad.OK, r.getEstadoIntegridad());
        assertEquals(1, r.getFilial().getTotal());
    }

    @Test
    void estadoNuloCuentaComoOtrasYNullContraNullNoEsDiferencia() {
        List<Fila> filas = Collections.singletonList(fila(1, null));
        ConvenioSucursalIntegridad r = comparar(filas, filas, null);
        assertEquals(1, r.getCentral().getOtras());
        assertEquals(ConvenioSucursalIntegridad.OK, r.getEstadoIntegridad());
    }

    @Test
    void filialVaciaContraCentralConDatos() {
        ConvenioSucursalIntegridad r = comparar(Collections.singletonList(fila(5, "ABIERTO")), new ArrayList<>(), null);
        assertEquals(ConvenioSucursalIntegridad.DIFERENCIA, r.getEstadoIntegridad());
        assertEquals(Collections.singletonList(5L), r.getIdsSobranEnCentral());
        assertNull(r.getUltimoFilial());
    }

    @Test
    void filialNoConsultadaDejaFilialNull() {
        ConvenioSucursalIntegridad r = comparar(Collections.singletonList(fila(5, "ABIERTO")), null, null);
        assertNull(r.getFilial());
        assertNull(r.getEstadoIntegridad());
        assertEquals(1, r.getCentral().getTotal());
    }

    @Test
    void topeDeIdsPeroCantidadReal() {
        List<Fila> filial = new ArrayList<>();
        for (long i = 1; i <= 80; i++) filial.add(fila(i, "ABIERTO"));
        ConvenioSucursalIntegridad r = comparar(new ArrayList<>(), filial, null);
        assertEquals(80, r.getFaltanEnCentral());
        assertEquals(ConvenioIntegridadService.TOPE_IDS, r.getIdsFaltanEnCentral().size());
        assertEquals(1L, r.getIdsFaltanEnCentral().get(0));
    }

    @Test
    void categoriaErrorNoExponeDetalle() {
        assertEquals("Autenticación rechazada",
                ConvenioIntegridadService.categoriaError(new SQLException("password authentication failed for user franco", "28P01")));
        assertEquals("Conexión rechazada",
                ConvenioIntegridadService.categoriaError(new PSQLException("Connection to 172.25.1.3:5551 refused", PSQLState.CONNECTION_UNABLE_TO_CONNECT)));
        assertEquals("Sin respuesta (timeout)",
                ConvenioIntegridadService.categoriaError(new SQLException("x", "08001", new SocketTimeoutException("connect timed out"))));
        assertEquals("La consulta tardó demasiado",
                ConvenioIntegridadService.categoriaError(new SQLException("canceling statement", "57014")));
        assertEquals("Error de consulta", ConvenioIntegridadService.categoriaError(new RuntimeException("x")));
    }
}
