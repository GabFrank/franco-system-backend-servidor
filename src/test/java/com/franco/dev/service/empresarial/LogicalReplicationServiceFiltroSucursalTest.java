package com.franco.dev.service.empresarial;

import com.franco.dev.repository.configuracion.LocalRepository;
import com.franco.dev.service.configuraciones.ReplicationTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #154: la publicacion de subida de cada filial filtra por sucursal_id. */
class LogicalReplicationServiceFiltroSucursalTest {

    private static final String PUB = "bodega_filial7_pub";
    private static final String TABLA = "operaciones.venta";
    private static final String AJENAS = "SELECT EXISTS (SELECT 1 FROM " + TABLA + " WHERE sucursal_id IS DISTINCT FROM ?)";
    private static final String WHERE = " WHERE (sucursal_id = 7)";

    private JdbcTemplate filial;
    private Connection con;
    private Statement st;
    private LogicalReplicationService service;

    @BeforeEach
    void setUp() throws SQLException {
        filial = mock(JdbcTemplate.class);
        con = mock(Connection.class);
        st = mock(Statement.class);
        when(con.getAutoCommit()).thenReturn(true);
        when(con.createStatement()).thenReturn(st);
        // execute(ConnectionCallback) corre el callback contra la conexion mockeada, y traduce el
        // SQLException como lo hace JdbcTemplate
        when(filial.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            try {
                return ((ConnectionCallback<?>) inv.getArgument(0)).doInConnection(con);
            } catch (SQLException e) {
                throw new org.springframework.jdbc.UncategorizedSQLException("test", null, e);
            }
        });
        service = new LogicalReplicationService(mock(JdbcTemplate.class), mock(SucursalService.class),
                mock(ReplicationTableService.class), mock(LocalRepository.class));
    }

    private void motivo(String motivo) {
        when(filial.queryForObject(LogicalReplicationService.SQL_MOTIVO_SIN_FILTRO, String.class, TABLA)).thenReturn(motivo);
    }

    private PreparedStatement psAjenas;

    private void ajenas(Boolean hay) throws SQLException {
        psAjenas = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(con.prepareStatement(AJENAS)).thenReturn(psAjenas);
        when(psAjenas.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getObject(1)).thenReturn(hay);
    }

    private void tieneFiltro(Boolean tiene) {
        when(filial.queryForObject(LogicalReplicationService.SQL_TIENE_FILTRO, Boolean.class, PUB, TABLA)).thenReturn(tiene);
    }

    // ── filtroSucursal ──────────────────────────────────────────────────────

    @Test
    void tablaFiltrableSinFilasAjenasLlevaElFiltro() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        ajenas(false);

        assertEquals(WHERE, service.filtroSucursal(filial, 7L, TABLA, new StringBuilder(), ""));
    }

    @Test
    void tablaQueNoSePuedeFiltrarQuedaSinFiltroYDiceElMotivo() throws SQLException {
        motivo("sin columna sucursal_id");
        StringBuilder log = new StringBuilder();

        assertEquals("", service.filtroSucursal(filial, 7L, TABLA, log, ""));
        assertTrue(log.toString().contains(TABLA + " sin filtro por sucursal: sin columna sucursal_id"), log.toString());
    }

    @Test
    void tablaConFilasAjenasQuedaSinFiltro() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        ajenas(true);
        StringBuilder log = new StringBuilder();

        assertEquals("", service.filtroSucursal(filial, 7L, TABLA, log, ""));
        assertTrue(log.toString().contains("tiene filas de otra sucursal o sin sucursal"), log.toString());
    }

    @Test
    void siElMotivoVieneNullQuedaSinFiltro() throws SQLException {
        // lo que devuelve un JdbcTemplate mockeado sin stub, o una respuesta inesperada
        assertEquals("", service.filtroSucursal(filial, 7L, TABLA, new StringBuilder(), ""));
    }

    @Test
    void siLaConsultaFallaQuedaSinFiltro() throws SQLException {
        when(filial.queryForObject(LogicalReplicationService.SQL_MOTIVO_SIN_FILTRO, String.class, TABLA))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"));
        assertEquals("", service.filtroSucursal(filial, 7L, TABLA, new StringBuilder(), ""));
    }

    @Test
    void siNoSePuedenContarLasFilasAjenasQuedaSinFiltro() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        ajenas(null);
        assertEquals("", service.filtroSucursal(filial, 7L, TABLA, new StringBuilder(), ""));
    }

    // ── agregarTablaAPublicacionFilial (tabla nueva en la pub) ──────────────

    @Test
    void tablaNuevaSeAgregaConFiltro() throws SQLException {
        when(filial.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, TABLA)).thenReturn(true);
        when(filial.queryForObject(anyString(), eq(String.class), anyString(), anyString())).thenReturn("f");
        motivo(LogicalReplicationService.FILTRABLE);
        ajenas(false);

        service.agregarTablaAPublicacionFilial(filial, 7L, PUB, TABLA, new StringBuilder());

        verify(filial).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA + WHERE);
    }

    // ── aplicarFiltroSucursal (tabla que ya estaba en la pub) ───────────────

    @Test
    void tablaSinFiltroSeFiltraEnUnaSolaTransaccion() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        tieneFiltro(false);
        ajenas(false);
        StringBuilder log = new StringBuilder();

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, log);

        InOrder orden = inOrder(con, st);
        orden.verify(con).setAutoCommit(false);
        orden.verify(st).execute("SET LOCAL lock_timeout = '5s'");
        orden.verify(st).execute("ALTER PUBLICATION " + PUB + " DROP TABLE " + TABLA);
        orden.verify(st).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA + WHERE);
        orden.verify(con).commit();
        orden.verify(con).setAutoCommit(true);
        verify(con, never()).rollback();
        assertTrue(log.toString().contains(TABLA + " filtrada por sucursal."), log.toString());
    }

    @Test
    void siFallaElAddSeHaceRollbackYSeLoguea() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        tieneFiltro(false);
        ajenas(false);
        doThrow(new SQLException("lock timeout")).when(st).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA + WHERE);
        StringBuilder log = new StringBuilder();

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, log);

        verify(con).rollback();
        verify(con, never()).commit();
        verify(con).setAutoCommit(true);
        assertTrue(log.toString().contains("error filtrando " + TABLA), log.toString());
    }

    @Test
    void tablaQueYaTieneFiltroNoSeToca() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        tieneFiltro(true);

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, new StringBuilder());

        verify(filial, never()).execute(any(ConnectionCallback.class));
        verify(filial, never()).execute(anyString());
    }

    @Test
    void tablaConFilasAjenasNoSeFiltra() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        tieneFiltro(false);
        ajenas(true);

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, new StringBuilder());

        verify(st, never()).execute(startsWith("ALTER PUBLICATION"));
        verify(con, never()).commit();
    }

    @Test
    void enPostgresViejoNoSeConsultaElFiltroNiSeToca() throws SQLException {
        motivo("PostgreSQL < 15");

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, new StringBuilder());

        // rowfilter no existe antes de PG 15
        verify(filial, never()).queryForObject(LogicalReplicationService.SQL_TIENE_FILTRO, Boolean.class, PUB, TABLA);
        verify(filial, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void tablaFiltradaQuePerdioLaIdentidadVuelveAFull() throws SQLException {
        when(filial.queryForObject(LogicalReplicationService.SQL_MOTIVO_SIN_FILTRO, String.class, TABLA))
                .thenReturn(LogicalReplicationService.MOTIVO_IDENTIDAD, LogicalReplicationService.FILTRABLE);
        tieneFiltro(true);
        StringBuilder log = new StringBuilder();

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, log);

        InOrder orden = inOrder(con, st);
        orden.verify(st).execute("SET LOCAL lock_timeout = '5s'");
        orden.verify(st).execute("ALTER TABLE " + TABLA + " REPLICA IDENTITY FULL");
        orden.verify(con).commit();
        verify(st, never()).execute(startsWith("ALTER PUBLICATION"));
        assertTrue(log.toString().contains("la identidad de replica ya no cubre sucursal_id"), log.toString());
        assertTrue(log.toString().contains("REPLICA IDENTITY FULL aplicado a " + TABLA), log.toString());
    }

    @Test
    void siNoSePuedePasarAFullSeLeSacaElFiltro() throws SQLException {
        motivo(LogicalReplicationService.MOTIVO_IDENTIDAD);
        tieneFiltro(true);
        doThrow(new SQLException("lock timeout")).when(st).execute("ALTER TABLE " + TABLA + " REPLICA IDENTITY FULL");
        StringBuilder log = new StringBuilder();

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, log);

        InOrder orden = inOrder(st, con);
        orden.verify(st).execute("ALTER PUBLICATION " + PUB + " DROP TABLE " + TABLA);
        orden.verify(st).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA);
        orden.verify(con).commit();
        assertTrue(log.toString().contains("se saco porque la identidad de replica no lo cubre"), log.toString());
    }

    @Test
    void elConteoDeFilasAjenasTieneTope() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        ajenas(false);

        service.filtroSucursal(filial, 7L, TABLA, new StringBuilder(), "");

        verify(psAjenas).setQueryTimeout(LogicalReplicationService.SEGUNDOS_FILAS_AJENAS);
        verify(psAjenas).setLong(1, 7L);
    }

    @Test
    void unaRuntimeExceptionTambienHaceRollback() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        tieneFiltro(false);
        ajenas(false);
        doThrow(new IllegalStateException("inesperado")).when(st).execute("ALTER PUBLICATION " + PUB + " ADD TABLE " + TABLA + WHERE);

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, new StringBuilder());

        verify(con).rollback();
        verify(con, never()).commit();
    }

    @Test
    void identidadQueNoCubreEnTablaSinFiltroNoSeTocaLaTabla() throws SQLException {
        motivo(LogicalReplicationService.MOTIVO_IDENTIDAD);
        tieneFiltro(false);

        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, new StringBuilder());

        verify(filial, never()).execute(startsWith("ALTER TABLE"));
        verify(filial, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void siFallaElDropNoSeHaceElAdd() throws SQLException {
        motivo(LogicalReplicationService.FILTRABLE);
        tieneFiltro(false);
        ajenas(false);
        doThrow(new SQLException("drop fallo")).when(st).execute(startsWith("ALTER PUBLICATION " + PUB + " DROP"));

        // el error no sale de aplicarFiltroSucursal: queda en el log y el sync sigue con la proxima tabla
        StringBuilder log = new StringBuilder();
        service.aplicarFiltroSucursal(filial, 7L, PUB, TABLA, log);

        verify(st, never()).execute(startsWith("ALTER PUBLICATION " + PUB + " ADD"));
        verify(con).rollback();
        assertTrue(log.toString().contains("drop fallo"), log.toString());
    }
}
