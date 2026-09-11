package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlineacionIdsFilialServiceTest {

    private static final String FUNCION = "SELECT configuraciones.alinear_secuencia_par(?::regclass, ?::regclass, ?)";

    private JdbcTemplate central;
    private SucursalService sucursalService;
    private LogicalReplicationService replicationService;
    private AlineacionIdsFilialService service;

    @BeforeEach
    void setUp() {
        central = mock(JdbcTemplate.class);
        sucursalService = mock(SucursalService.class);
        replicationService = mock(LogicalReplicationService.class);
        service = new AlineacionIdsFilialService(central, sucursalService, replicationService);
        when(replicationService.queryRemoteForObject(anyLong(), anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(100L);
    }

    private Sucursal sucursal(Long id, boolean activa, String ip) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre("SUC " + id);
        s.setActivo(activa);
        s.setIp(ip);
        s.setPuerto(ip == null ? null : 5432);
        return s;
    }

    @Test
    void usaElMayorParDelCentralDeEsaSucursal() {
        when(central.queryForObject(contains("inicio_sesion WHERE id % 2 = 0 AND sucursal_id"), eq(Long.class), eq(2L)))
                .thenReturn(7064L);

        service.alinear(2L);

        verify(replicationService).queryRemoteForObject(2L, FUNCION, Long.class,
                "configuraciones.inicio_sesion_id_seq", "configuraciones.inicio_sesion", 7064L);
    }

    @Test
    void enLasTablasDeIdGlobalMiraTodaLaTabla() {
        when(central.queryForObject(contains("financiero.maletin WHERE id % 2 = 0"), eq(Long.class)))
                .thenReturn(24L);

        service.alinear(2L);

        verify(replicationService).queryRemoteForObject(2L, FUNCION, Long.class,
                "financiero.maletin_id_seq", "financiero.maletin", 24L);
    }

    @Test
    void sinParesEnElCentralMandaCero() {
        service.alinear(2L);

        verify(replicationService).queryRemoteForObject(2L, FUNCION, Long.class,
                "financiero.gasto_id_seq", "financiero.gasto", 0L);
    }

    @Test
    void unFilialQueFallaNoFrenaALosDemas() {
        when(sucursalService.findAllExcludingServer()).thenReturn(Arrays.asList(
                sucursal(2L, true, "10.0.0.2"), sucursal(3L, true, "10.0.0.3")));
        when(replicationService.queryRemoteForObject(eq(2L), anyString(), eq(Long.class), any(), any(), any()))
                .thenThrow(new RuntimeException("function does not exist"));

        AlineacionIdsFilialService.Resultado resultado = service.alinearTodas();

        assertFalse(resultado.isSuccess());
        assertTrue(resultado.getMensaje().contains("SUC 2 (2): ERROR function does not exist"));
        assertTrue(resultado.getMensaje().contains("SUC 3 (3): configuraciones.inicio_sesion -> 100"));
    }

    @Test
    void ignoraSucursalesInactivasOSinIp() {
        when(sucursalService.findAllExcludingServer()).thenReturn(Arrays.asList(
                sucursal(4L, false, "10.0.0.4"), sucursal(5L, true, null)));

        AlineacionIdsFilialService.Resultado resultado = service.alinearTodas();

        assertTrue(resultado.isSuccess());
        assertEquals("No hay filiales activos con IP y puerto", resultado.getMensaje());
        verify(replicationService, never()).queryRemoteForObject(anyLong(), anyString(), eq(Long.class), any(), any(), any());
    }
}
