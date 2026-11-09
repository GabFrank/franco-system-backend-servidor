package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.graphql.financiero.input.EjecutarRetiroPreGastoInput;
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

import java.math.BigDecimal;
import java.util.Collections;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * El vuelto cargado en la caja (PDV o caja mayor) pisa la rendición del teléfono: es lo que de verdad volvió.
 */
class PreGastoVueltoTest {

    private PreGastoRepository repository;
    private GastoRendicionRepository rendiciones;
    private PreGastoService service;

    @BeforeEach
    void setUp() {
        repository = mock(PreGastoRepository.class);
        rendiciones = mock(GastoRendicionRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new PreGastoService(
                repository,
                mock(EnteFinancieroService.class),
                mock(EnteCuotaService.class),
                mock(SolicitudPagoService.class),
                mock(ProveedorService.class),
                mock(UsuarioService.class),
                mock(PersonaService.class),
                mock(FuncionarioService.class),
                mock(AutorizacionAuditService.class),
                mock(MuebleService.class),
                mock(EnteService.class),
                mock(InmuebleService.class),
                mock(VehiculoService.class),
                mock(EquipoService.class),
                mock(GastoRepository.class),
                rendiciones,
                mock(PreGastoDetalleFinanzasService.class),
                mock(PdvCajaService.class),
                mock(MonedaService.class),
                mock(TipoGastoService.class),
                mock(TipoGastoModuloReglasService.class));
    }

    private PreGasto retirada(String retirado, String rendidoTelefono) {
        PreGasto pg = new PreGasto();
        pg.setId(2L);
        pg.setSucursalId(0L);
        pg.setEstado(EstadoPreGasto.PAGADO);
        pg.setMontoRetirado(new BigDecimal(retirado));
        pg.setMontoGastado(new BigDecimal(rendidoTelefono));
        return pg;
    }

    @Test
    void elVueltoPisaLaRendicionDelTelefono() {
        PreGasto pg = service.aplicarVueltoYGuardar(retirada("56000", "9000"), new BigDecimal("40000"));

        assertEquals(0, new BigDecimal("16000").compareTo(pg.getMontoGastado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(pg.getSaldoDevolver()));
        assertEquals("RENDIDO_COMPLETO", pg.getEstadoRendicion());
        assertNotNull(pg.getFechaRendicion());
    }

    @Test
    void rindioTotalmenteEsVueltoCero() {
        PreGasto pg = service.aplicarVueltoYGuardar(retirada("56000", "9000"), BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("56000").compareTo(pg.getMontoGastado()));
        assertEquals("RENDIDO_COMPLETO", pg.getEstadoRendicion());
    }

    @Test
    void sinVueltoLaRendicionVuelveAlTelefono() {
        GastoRendicion r = new GastoRendicion();
        r.setMontoTotal(new BigDecimal("9000"));
        when(rendiciones.findByPreGastoIdAndSucursalId(2L, 0L)).thenReturn(Collections.singletonList(r));
        PreGasto pg = retirada("56000", "16000");
        pg.setMontoVuelto(new BigDecimal("40000"));

        pg = service.aplicarVueltoYGuardar(pg, null);

        assertNull(pg.getMontoVuelto());
        assertEquals(0, new BigDecimal("9000").compareTo(pg.getMontoGastado()));
        assertEquals(0, new BigDecimal("47000").compareTo(pg.getSaldoDevolver()));
        assertEquals("RENDIDO_PARCIAL", pg.getEstadoRendicion());
    }
}
