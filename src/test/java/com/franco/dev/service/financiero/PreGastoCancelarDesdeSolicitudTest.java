package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.GastoRendicionRepository;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.repository.financiero.PreGastoRepository;
import com.franco.dev.service.activos.EnteService;
import com.franco.dev.service.activos.InmuebleService;
import com.franco.dev.service.activos.MuebleService;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.service.administrativo.AutorizacionAuditService;
import com.franco.dev.service.equipos.EquipoService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tesorería cancela la solicitud GASTO de un PreGasto (#599): el PreGasto queda RECHAZADO, conserva su
 * autorización, y no se puede volver a autorizar.
 */
class PreGastoCancelarDesdeSolicitudTest {

    private PreGastoRepository repository;
    private AutorizacionAuditService audit;
    private PreGastoService service;
    private SolicitudPago sp;
    private PreGasto pg;
    private Usuario usuario;
    private Persona autorizador;

    @BeforeEach
    void setUp() {
        repository = mock(PreGastoRepository.class);
        audit = mock(AutorizacionAuditService.class);
        service = spy(new PreGastoService(repository, mock(EnteFinancieroService.class), mock(EnteCuotaService.class),
                mock(SolicitudPagoService.class), mock(ProveedorService.class), mock(UsuarioService.class),
                mock(PersonaService.class), mock(FuncionarioService.class), audit, mock(MuebleService.class),
                mock(EnteService.class), mock(InmuebleService.class), mock(VehiculoService.class),
                mock(EquipoService.class), mock(GastoRepository.class), mock(GastoRendicionRepository.class),
                mock(PreGastoDetalleFinanzasService.class), mock(PdvCajaService.class), mock(MonedaService.class),
                mock(TipoGastoService.class), mock(TipoGastoModuloReglasService.class)));

        sp = new SolicitudPago();
        sp.setId(30L);

        autorizador = new Persona();
        autorizador.setId(9L);
        pg = new PreGasto();
        pg.setId(4L);
        pg.setSucursalId(2L);
        pg.setEstado(EstadoPreGasto.ENVIADO_A_TESORERIA);
        pg.setSolicitudPagoId(30L);
        pg.setAutorizadoPor(autorizador);
        LocalDateTime autorizadoEn = LocalDateTime.of(2026, 11, 10, 9, 0);
        pg.setAutorizadoEn(autorizadoEn);
        when(repository.findBySolicitudPagoId(30L)).thenReturn(pg);
        when(repository.findByIdAndSucursalId(4L, 2L)).thenReturn(pg);

        Persona p = new Persona();
        p.setId(77L);
        usuario = new Usuario();
        usuario.setPersona(p);
    }

    @Test
    void quedaRechazadoConElMotivoYConservaLaAutorizacion() {
        service.cancelarDesdeSolicitudPago(sp, "CANCELADA POR JPEREZ (estaba SOLICITADO): DUPLICADO", usuario);

        assertEquals(EstadoPreGasto.RECHAZADO, pg.getEstado());
        assertEquals("CANCELADA POR JPEREZ (estaba SOLICITADO): DUPLICADO", pg.getMotivoRechazo());
        assertNotNull(pg.getRechazadoEn());
        assertEquals(77L, pg.getRechazadoPor().getId());
        assertSame(autorizador, pg.getAutorizadoPor());
        assertNotNull(pg.getAutorizadoEn());
        assertEquals(30L, pg.getSolicitudPagoId());
        // Por el repositorio: el save del servicio revalida el monto contra la cuota del activo.
        verify(repository).save(pg);
        verify(service, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void enOtroEstadoOSinPreGastoNoHaceNada() {
        pg.setEstado(EstadoPreGasto.PAGADO);
        service.cancelarDesdeSolicitudPago(sp, "x", usuario);
        assertEquals(EstadoPreGasto.PAGADO, pg.getEstado());

        when(repository.findBySolicitudPagoId(30L)).thenReturn(null);
        service.cancelarDesdeSolicitudPago(sp, "x", usuario);

        verify(repository, never()).save(any());
    }

    @Test
    void unRechazoPorCancelacionNoSeVuelveAAutorizar() {
        pg.setEstado(EstadoPreGasto.RECHAZADO);

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.autorizar(4L, 9L, 1L, 2L));

        assertTrue(e.getMessage().contains("se canceló en tesorería"), e.getMessage());
        assertEquals(EstadoPreGasto.RECHAZADO, pg.getEstado());
        verify(repository, never()).save(any());
    }

    @Test
    void unRechazoNormalSinSolicitudSeAutorizaComoSiempre() {
        pg.setEstado(EstadoPreGasto.RECHAZADO);
        pg.setSolicitudPagoId(null);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.autorizar(4L, 9L, 1L, 2L);

        assertEquals(EstadoPreGasto.AUTORIZADO, pg.getEstado());
    }
}
