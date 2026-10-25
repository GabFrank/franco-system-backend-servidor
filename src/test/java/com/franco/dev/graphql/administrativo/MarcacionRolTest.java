package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.RrhhSecurityFalso;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * saveMarcacion: marcar sigue abierto a cualquier sesion (kioscos del desktop), pero sin RRHH GESTIONAR no se
 * sobrescribe una marcacion y la hora la pone el servidor.
 */
class MarcacionRolTest {

    private MarcacionService service;
    private MarcacionGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(MarcacionService.class);
        resolver = new MarcacionGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(resolver, "sucursalService", mock(SucursalService.class));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void sesion(String... roles) {
        ReflectionTestUtils.setField(resolver, "seg", new RrhhSecurityFalso(roles));
    }

    private static MarcacionInput marcacion(TipoMarcacion tipo) {
        MarcacionInput in = new MarcacionInput();
        in.setSucursalId(13L);
        in.setUsuarioId(40L);
        in.setTipo(tipo);
        return in;
    }

    private Marcacion recibida() {
        ArgumentCaptor<Marcacion> c = ArgumentCaptor.forClass(Marcacion.class);
        verify(service).save(c.capture());
        return c.getValue();
    }

    @Test
    void sinRolSobrescribirUnaMarcacionSeRechazaSinGuardar() {
        sesion();
        MarcacionInput in = marcacion(TipoMarcacion.ENTRADA);
        in.setId(99L);
        assertThrows(GraphQLException.class, () -> resolver.saveMarcacion(in));
        verifyNoInteractions(service);
    }

    @Test
    void sinRolLaHoraDelClienteSeIgnora() {
        sesion();
        MarcacionInput in = marcacion(TipoMarcacion.ENTRADA);
        in.setFechaEntrada("2026-10-24T06:00:00");
        resolver.saveMarcacion(in);
        Marcacion m = recibida();
        assertNull(m.getFechaEntrada());
    }

    @Test
    void unaSalidaSinTipoSigueSiendoSalida() {
        sesion();
        MarcacionInput in = marcacion(null);
        in.setFechaSalida("2026-10-24T18:00:00");
        resolver.saveMarcacion(in);
        Marcacion m = recibida();
        assertEquals(TipoMarcacion.SALIDA, m.getTipo());
        assertNull(m.getFechaSalida());
    }

    @Test
    void conRrhhGestionarSeRespetaLaHoraYSePuedeCorregir() {
        sesion("RRHH GESTIONAR");
        MarcacionInput in = marcacion(TipoMarcacion.ENTRADA);
        in.setFechaEntrada("2026-10-24T06:00:00");
        resolver.saveMarcacion(in);
        assertEquals(LocalDateTime.of(2026, 10, 24, 6, 0), recibida().getFechaEntrada());
    }
}
