package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PreGasto;
import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import com.franco.dev.graphql.financiero.input.EjecutarRetiroPreGastoInput;
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
import static org.mockito.Mockito.*;

/**
 * Una solicitud dirigida a SERVIDOR (sucursal 0) se cobra desde la caja mayor, no por retiro de
 * caja: la sucursal 0 no tiene ninguna pdv_caja. Sin este corte el flujo moria mas adelante con
 * "Debe registrar el gasto en la caja local", que manda al operador a hacer justo lo que no puede.
 */
class PreGastoRetiroServidorTest {

    private PreGastoRepository repository;
    private PreGastoService service;

    @BeforeEach
    void setUp() {
        repository = mock(PreGastoRepository.class);
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
                mock(PreGastoDetalleFinanzasService.class),
                mock(PdvCajaService.class),
                mock(MonedaService.class),
                mock(TipoGastoService.class),
                mock(TipoGastoModuloReglasService.class));
    }

    /** PreGasto listo para retirar, salvo por la sucursal. */
    private PreGasto preGastoAutorizado(Long sucursalId) {
        PreGasto pg = new PreGasto();
        pg.setId(1L);
        pg.setSucursalId(sucursalId);
        pg.setEstado(EstadoPreGasto.AUTORIZADO);
        pg.setRetiroConfirmadoEn(LocalDateTime.now());
        return pg;
    }

    private EjecutarRetiroPreGastoInput input(Long sucursalId) {
        EjecutarRetiroPreGastoInput in = new EjecutarRetiroPreGastoInput();
        in.setPreGastoId(1L);
        in.setSucursalId(sucursalId);
        in.setGastoRegistroId(77L);
        in.setCajaId(5L);
        return in;
    }

    @Test
    void retiroDeCajaRechazadoParaSolicitudDeServidor() {
        when(repository.findByIdAndSucursalId(1L, 0L)).thenReturn(preGastoAutorizado(0L));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.ejecutarRetiro(input(0L)));

        assertTrue(ex.getMessage().toUpperCase().contains("SERVIDOR"),
                "El mensaje tiene que nombrar a SERVIDOR: " + ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("caja mayor"),
                "El mensaje tiene que mandar a la caja mayor: " + ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void seCreaLaSolicitudDirigidaAServidor() {
        when(repository.findMaxId(0L)).thenReturn(null);
        when(repository.save(any(PreGasto.class))).thenAnswer(i -> i.getArgument(0));

        PreGasto nueva = new PreGasto();
        nueva.setSucursalId(0L);

        PreGasto guardada = service.save(nueva);

        assertEquals(0L, guardada.getSucursalId());
        assertEquals(1L, guardada.getId(), "El correlativo de SERVIDOR arranca en 1 como el de cualquier sucursal");
        assertEquals(EstadoPreGasto.PENDIENTE, guardada.getEstado());
    }

    @Test
    void sigueRechazandoLaSolicitudSinNingunaSucursal() {
        // Aceptar el 0 no puede convertirse en "cualquier cosa vale": sin sucursal sigue siendo
        // un error, no un default silencioso.
        PreGasto nueva = new PreGasto();
        nueva.setSucursalId(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.save(nueva));

        assertTrue(ex.getMessage().toLowerCase().contains("sucursal"), ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void retiroDeCajaSigueValidoParaUnaFilial() {
        when(repository.findByIdAndSucursalId(1L, 3L)).thenReturn(preGastoAutorizado(3L));

        // No llega a completarse (los colaboradores son mocks), pero NO puede cortar por el
        // guardarraíl de SERVIDOR: una filial sí retira de su caja.
        try {
            service.ejecutarRetiro(input(3L));
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            assertFalse(msg.toUpperCase().contains("SERVIDOR"),
                    "Una filial no puede caer en el corte de SERVIDOR: " + msg);
        }
    }
}
