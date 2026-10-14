package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSucursal;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSucursalRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Las sucursales adicionales de una caja se guardan como conjunto: queda exactamente lo pedido, sin la
 * sucursal propia de la caja y sin repetidos.
 */
class CajaVirtualSucursalServiceTest {

    private static final Long CAJA = 19L;

    private CajaVirtualSucursalRepository repository;
    private SucursalRepository sucursalRepository;
    private CajaVirtualSucursalService service;
    private final List<CajaVirtualSucursal> actuales = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(CajaVirtualSucursalRepository.class);
        CajaVirtualRepository cajaVirtualRepository = mock(CajaVirtualRepository.class);
        sucursalRepository = mock(SucursalRepository.class);
        service = new CajaVirtualSucursalService(repository, cajaVirtualRepository, sucursalRepository);

        CajaVirtual caja = new CajaVirtual();
        caja.setId(CAJA);
        caja.setSucursal(sucursal(12L));
        when(cajaVirtualRepository.findById(CAJA)).thenReturn(Optional.of(caja));
        when(repository.findByCajaVirtualIdOrderByIdAsc(CAJA)).thenReturn(actuales);
        when(sucursalRepository.findById(anyLong())).thenAnswer(i -> Optional.of(sucursal(i.getArgument(0))));
    }

    private static Sucursal sucursal(Long id) {
        Sucursal s = new Sucursal();
        s.setId(id);
        return s;
    }

    private CajaVirtualSucursal yaTiene(Long sucursalId) {
        CajaVirtualSucursal cs = new CajaVirtualSucursal();
        cs.setSucursal(sucursal(sucursalId));
        actuales.add(cs);
        return cs;
    }

    private List<Long> agregadas() {
        ArgumentCaptor<CajaVirtualSucursal> c = ArgumentCaptor.forClass(CajaVirtualSucursal.class);
        verify(repository, atLeast(0)).save(c.capture());
        List<Long> ids = new ArrayList<>();
        for (CajaVirtualSucursal cs : c.getAllValues()) ids.add(cs.getSucursal().getId());
        return ids;
    }

    @Test
    void agrega_las_sucursales_pedidas() {
        service.guardar(CAJA, Arrays.asList(9L, 23L), null);

        assertEquals(Arrays.asList(9L, 23L), agregadas());
        verify(repository, never()).delete(any());
    }

    @Test
    void la_sucursal_propia_de_la_caja_y_los_repetidos_no_se_guardan() {
        service.guardar(CAJA, Arrays.asList(12L, 9L, 9L, null), null);

        assertEquals(Collections.singletonList(9L), agregadas());
    }

    @Test
    void quita_las_que_ya_no_se_piden_y_no_toca_las_que_siguen() {
        CajaVirtualSucursal nueve = yaTiene(9L);
        CajaVirtualSucursal veintitres = yaTiene(23L);

        service.guardar(CAJA, Arrays.asList(9L, 7L), null);

        verify(repository).delete(veintitres);
        verify(repository, never()).delete(nueve);
        assertEquals(Collections.singletonList(7L), agregadas());
    }

    @Test
    void una_lista_vacia_las_quita_todas() {
        CajaVirtualSucursal nueve = yaTiene(9L);

        service.guardar(CAJA, Collections.emptyList(), null);

        verify(repository).delete(nueve);
        assertTrue(agregadas().isEmpty());
    }

    @Test
    void una_sucursal_que_no_existe_lo_dice() {
        when(sucursalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(GraphQLException.class, () -> service.guardar(CAJA, Collections.singletonList(99L), null));
    }

    @Test
    void una_caja_que_no_existe_lo_dice() {
        assertThrows(GraphQLException.class, () -> service.guardar(77L, Collections.singletonList(9L), null));
        verify(repository, never()).save(any());
    }
}
