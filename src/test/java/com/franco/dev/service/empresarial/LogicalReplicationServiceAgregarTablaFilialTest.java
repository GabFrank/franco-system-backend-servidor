package com.franco.dev.service.empresarial;

import com.franco.dev.repository.configuracion.LocalRepository;
import com.franco.dev.service.configuraciones.ReplicationTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogicalReplicationServiceAgregarTablaFilialTest {

    private static final String EXISTE = "SELECT to_regclass(?) IS NOT NULL";
    private static final String PUB = "bodega_filial7_pub";
    private static final String TABLA = "financiero.venta_tarjeta";

    private JdbcTemplate filial;
    private LogicalReplicationService service;

    @BeforeEach
    void setUp() {
        filial = mock(JdbcTemplate.class);
        service = new LogicalReplicationService(mock(JdbcTemplate.class), mock(SucursalService.class),
                mock(ReplicationTableService.class), mock(LocalRepository.class));
        // ensureReplicaIdentityFull: la tabla ya esta en FULL
        when(filial.queryForObject(anyString(), eq(String.class), anyString(), anyString())).thenReturn("f");
    }

    @Test
    void tablaQueNoExisteEnLaFilialSeOmiteSinAlter() {
        when(filial.queryForObject(EXISTE, Boolean.class, TABLA)).thenReturn(false);
        StringBuilder log = new StringBuilder();

        service.agregarTablaAPublicacionFilial(filial, 7L, PUB, TABLA, log);

        verify(filial, never()).execute(startsWith("ALTER PUBLICATION"));
        verify(filial, never()).execute(startsWith("ALTER TABLE"));
        assertTrue(log.toString().contains(TABLA + " no existe en la filial, omitida."), log.toString());
    }

    @Test
    void tablaQueExisteSeAgregaALaPublicacion() {
        when(filial.queryForObject(EXISTE, Boolean.class, TABLA)).thenReturn(true);
        StringBuilder log = new StringBuilder();

        service.agregarTablaAPublicacionFilial(filial, 7L, PUB, TABLA, log);

        verify(filial).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA);
        assertTrue(log.toString().contains("agregada tabla " + TABLA), log.toString());
    }

    @Test
    void tablaQueYaEstabaEnLaPublicacionQuedaOk() {
        when(filial.queryForObject(EXISTE, Boolean.class, TABLA)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("relation is already in publication"))
                .when(filial).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA);
        StringBuilder log = new StringBuilder();

        service.agregarTablaAPublicacionFilial(filial, 7L, PUB, TABLA, log);

        assertTrue(log.toString().contains(TABLA + " ya estaba (OK)."), log.toString());
    }
}
