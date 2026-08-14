package com.franco.dev.service.configuracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La limpieza borra todo el modulo de notificaciones. Como es destructiva,
 * importa tanto que vacie las cuatro tablas como que respete los flags que la
 * apagan.
 */
class NotificacionCleanupServiceTest {

    private EntityManager entityManager;
    private Query query;
    private NotificacionCleanupService service;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(123L);

        service = new NotificacionCleanupService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        ReflectionTestUtils.setField(service, "cleanupEnabled", true);
        ReflectionTestUtils.setField(service, "limpiarAlArrancar", true);
    }

    private String sqlDeVaciado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(2)).createNativeQuery(captor.capture());
        return captor.getAllValues().stream()
                .filter(sql -> sql.startsWith("TRUNCATE"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se ejecuto ningun TRUNCATE"));
    }

    @Test
    void alArrancarSeVacianLasCuatroTablasDelModulo() {
        service.limpiezaAlArrancar();

        String sql = sqlDeVaciado();
        assertTrue(sql.contains("configuraciones.notificacion_envio_log"), sql);
        assertTrue(sql.contains("configuraciones.notificacion_destinatario"), sql);
        assertTrue(sql.contains("configuraciones.notificacion_comentario"), sql);
        assertTrue(sql.contains("configuraciones.notificacion"), sql);
        verify(query).executeUpdate();
    }

    @Test
    void noSeUsaCascadeParaQueUnaTablaNuevaFalleEnVezDeVaciarseSola() {
        service.limpiezaAlArrancar();

        assertTrue(!sqlDeVaciado().contains("CASCADE"),
                "con CASCADE una tabla hija nueva se vaciaria sin que nadie lo decida");
    }

    @Test
    void laCorridaProgramadaVaciaIgual() {
        service.limpiezaProgramada();

        verify(query).executeUpdate();
    }

    @Test
    void elFlagGeneralApagaTambienElArranque() {
        ReflectionTestUtils.setField(service, "cleanupEnabled", false);

        service.limpiezaAlArrancar();
        service.limpiezaProgramada();

        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void sePuedeApagarSoloLaLimpiezaDeArranque() {
        ReflectionTestUtils.setField(service, "limpiarAlArrancar", false);

        service.limpiezaAlArrancar();
        verify(entityManager, never()).createNativeQuery(anyString());

        service.limpiezaProgramada();
        verify(query).executeUpdate();
    }

    @Test
    void unFalloNoTumbaElArranqueDelServidor() {
        when(query.executeUpdate()).thenThrow(new IllegalStateException("tabla bloqueada"));

        service.limpiezaAlArrancar();
    }

    @Test
    void elIntervaloPorDefectoEsDeSieteDias() {
        assertEquals(604800000L, service.intervaloPorDefectoMs());
    }
}
