package com.franco.dev.service.personas;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.empresarial.SucursalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Backfill del pais (#666): graba solo lo que no choca, informa lo que no pudo leer y no se corta por una fila. */
class PersonaPaisBackfillServiceTest {

    private SucursalService sucursalService;
    private PersonaService personaService;
    private JdbcTemplate jdbc;
    /** host de la filial -> lo que devuelve (Map) o la excepcion que tira */
    private final Map<String, Object> filiales = new HashMap<>();
    private PersonaPaisBackfillService service;

    private static Sucursal sucursal(long id, String ip) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre("SUC " + id);
        s.setIp(ip);
        s.setPuerto(5432);
        return s;
    }

    @BeforeEach
    void setUp() {
        sucursalService = mock(SucursalService.class);
        personaService = mock(PersonaService.class);
        jdbc = mock(JdbcTemplate.class);
        LogicalReplicationService repl = mock(LogicalReplicationService.class);
        when(repl.getBranchDbName()).thenReturn("general");
        when(repl.generateBranchSubscriptionName(anyLong())).thenAnswer(i -> "bodega_filial" + i.getArgument(0) + "_sub");
        service = new PersonaPaisBackfillService(sucursalService, repl, personaService, jdbc) {
            @SuppressWarnings("unchecked")
            @Override
            Map<Long, Long> leer(Destino d) throws SQLException {
                Object r = filiales.get(d.host);
                if (r instanceof SQLException) throw (SQLException) r;
                if (r == null) throw new SQLException("Connection attempt timed out.", "08001");
                return (Map<Long, Long>) r;
            }
        };
    }

    private static Map<Long, Long> filas(long... personaPais) {
        Map<Long, Long> m = new HashMap<>();
        for (int i = 0; i < personaPais.length; i += 2) m.put(personaPais[i], personaPais[i + 1]);
        return m;
    }

    private PersonaPaisBackfillService.Filial filial(PersonaPaisBackfillService.Resultado r, long id) {
        return r.getFiliales().stream().filter(f -> f.getSucursalId() == id).findFirst().orElseThrow(AssertionError::new);
    }

    @Test
    void grabaLoQueNoChocaEInformaConflictosYFiliales() {
        when(sucursalService.findAllExcludingServer()).thenReturn(Arrays.asList(
                sucursal(1, "10.0.0.1"), sucursal(2, "10.0.0.2"), sucursal(3, "10.0.0.3"),
                sucursal(4, "10.0.0.4"), sucursal(5, null)));
        filiales.put("10.0.0.1", filas(100, 30, 200, 30, 300, 10));
        filiales.put("10.0.0.2", filas(100, 30, 200, 40));               // 200: otro pais -> conflicto
        filiales.put("10.0.0.3", new SQLException("column \"pais_id\" does not exist", "42703"));
        // 10.0.0.4 no responde; la 5 no tiene IP ni suscripcion
        when(personaService.asignarPaisSiNulo(100L, 30L)).thenReturn(true);
        when(personaService.asignarPaisSiNulo(300L, 10L)).thenReturn(false); // ya tenia

        PersonaPaisBackfillService.Resultado r = service.ejecutar(null);

        assertEquals(1, r.getGrabados());
        assertEquals(Collections.singletonList(100L), r.getIdsGrabados());
        assertEquals(1, r.getSinCambios());
        assertEquals(Collections.singletonList(200L), r.getConflictos());
        verify(personaService, never()).asignarPaisSiNulo(eq(200L), anyLong());
        assertEquals(PersonaPaisBackfillService.OK, filial(r, 1).getEstado());
        assertEquals(3, filial(r, 1).getPersonasConPais());
        assertEquals(PersonaPaisBackfillService.SIN_COLUMNA, filial(r, 3).getEstado());
        assertEquals(PersonaPaisBackfillService.SIN_CONEXION, filial(r, 4).getEstado());
        assertEquals(PersonaPaisBackfillService.SIN_IP, filial(r, 5).getEstado());
        assertFalse(r.isCompleto());
        // sin la 4 (sin conexion) estaria completo: la 5 sin IP ni suscripcion no lo traba
        filiales.put("10.0.0.4", filas());
        filiales.put("10.0.0.3", filas());
        assertTrue(service.ejecutar(null).isCompleto());
    }

    @Test
    void unPaisHuerfanoNoCortaLaCorrida() {
        when(sucursalService.findAllExcludingServer()).thenReturn(Collections.singletonList(sucursal(1, "10.0.0.1")));
        filiales.put("10.0.0.1", filas(100, 999, 200, 30));
        when(personaService.asignarPaisSiNulo(100L, 999L)).thenThrow(new DataIntegrityViolationException("fk"));
        when(personaService.asignarPaisSiNulo(200L, 30L)).thenReturn(true);

        PersonaPaisBackfillService.Resultado r = service.ejecutar(null);

        assertEquals(Collections.singletonList(100L), r.getPaisesDesconocidos());
        assertEquals(Collections.singletonList(200L), r.getIdsGrabados());
        assertTrue(r.isCompleto());
    }

    @Test
    void siLaIpFallaUsaElHostDeLaSuscripcion() {
        when(sucursalService.findAllExcludingServer()).thenReturn(Collections.singletonList(sucursal(6, "172.25.1.6")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("bodega_filial6_sub")))
                .thenReturn(Collections.singletonList("dbname=general host=100.64.0.4 user=franco password=x port=5552"));
        filiales.put("100.64.0.4", filas(100, 30));
        when(personaService.asignarPaisSiNulo(100L, 30L)).thenReturn(true);

        PersonaPaisBackfillService.Resultado r = service.ejecutar(null);

        assertEquals(PersonaPaisBackfillService.OK, filial(r, 6).getEstado());
        assertEquals(1, r.getGrabados());
    }

    @Test
    void elConninfoSeLeeSinColarParametros() {
        PersonaPaisBackfillService.Destino d = PersonaPaisBackfillService.desdeConninfo(
                "dbname=general host=100.64.0.4 user=franco password=secreta port=5552");
        assertEquals("100.64.0.4", d.host);
        assertEquals(5552, d.puerto);
        assertEquals("general", d.base);
        assertNull(PersonaPaisBackfillService.desdeConninfo("dbname=general host=a/b port=5552"));
        assertNull(PersonaPaisBackfillService.desdeConninfo("dbname=general user=franco"));
    }

    @Test
    void conFiltroSoloConsultaEsasSucursales() {
        when(sucursalService.findAllExcludingServer()).thenReturn(Arrays.asList(sucursal(1, "10.0.0.1"), sucursal(24, "10.0.0.24")));
        filiales.put("10.0.0.24", filas(100, 30));
        when(personaService.asignarPaisSiNulo(100L, 30L)).thenReturn(true);

        PersonaPaisBackfillService.Resultado r = service.ejecutar(Collections.singletonList(24L));

        assertEquals(1, r.getFiliales().size());
        assertEquals(24L, r.getFiliales().get(0).getSucursalId());
        assertTrue(r.isCompleto());
    }
}
