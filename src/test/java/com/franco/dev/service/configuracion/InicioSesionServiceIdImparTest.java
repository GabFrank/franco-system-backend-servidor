package com.franco.dev.service.configuracion;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El filial genera los ids de inicio_sesion de su sucursal (pares) y los
 * replica al central. Si el central insertara un id que el filial tambien va a
 * generar, la replicacion choca con inicio_sesion_pk y se corta entera. Por eso
 * el central solo inserta impares, y nunca inserta un par que le manda un cliente.
 */
class InicioSesionServiceIdImparTest {

    private InicioSesionRepository repository;
    private InicioSesionService service;

    @BeforeEach
    void setUp() {
        repository = mock(InicioSesionRepository.class);
        service = new InicioSesionService(repository, mock(ApplicationEventPublisher.class));
        when(repository.save(any(InicioSesion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private InicioSesion sesion(Long id, Long sucursalId) {
        InicioSesion sesion = new InicioSesion();
        sesion.setId(id);
        sesion.setSucursalId(sucursalId);
        return sesion;
    }

    private void elCentralTiene(long id, long sucursalId, boolean existe) {
        when(repository.existsById(new EmbebedPrimaryKey(id, sucursalId))).thenReturn(existe);
    }

    @Test
    void unaSesionNuevaDelCentralEsImparAunqueElMaximoVengaDelFilial() {
        when(repository.findMaxId(2L)).thenReturn(7064L);

        assertEquals(7065L, service.save(sesion(null, 2L)).getId());
    }

    @Test
    void siguePorImparesDespuesDeOtraSesionDelCentral() {
        when(repository.findMaxId(2L)).thenReturn(7065L);

        assertEquals(7067L, service.save(sesion(null, 2L)).getId());
    }

    @Test
    void reabrirUnaSesionQueElCentralYaTieneConservaSuId() {
        elCentralTiene(7062L, 2L, true);

        InicioSesion guardada = service.save(sesion(7062L, 2L));

        assertEquals(7062L, guardada.getId(), "es un UPDATE de la fila que ya llego del filial");
        verify(repository, never()).findMaxId(anyLong());
    }

    @Test
    void unaSesionImparDelCentralNoSeConsulta() {
        InicioSesion guardada = service.save(sesion(7069L, 2L));

        assertEquals(7069L, guardada.getId());
        verify(repository, never()).existsById(any(EmbebedPrimaryKey.class));
    }

    @Test
    void abrirConUnIdDeFilialQueElCentralNoTieneCreaUnaSesionImpar() {
        elCentralTiene(7064L, 2L, false);
        when(repository.findMaxId(2L)).thenReturn(7063L);

        InicioSesion guardada = service.save(sesion(7064L, 2L));

        assertEquals(7065L, guardada.getId(),
                "insertar el 7064 haria chocar el INSERT que el filial todavia no replico");
    }

    @Test
    void cerrarUnaSesionDeFilialQueElCentralNoTieneNoEscribeNada() {
        elCentralTiene(7064L, 2L, false);
        InicioSesion cierre = sesion(7064L, 2L);
        cierre.setHoraFin(LocalDateTime.of(2026, 9, 11, 18, 0));

        service.save(cierre);

        verify(repository, never()).save(any(InicioSesion.class));
    }
}
