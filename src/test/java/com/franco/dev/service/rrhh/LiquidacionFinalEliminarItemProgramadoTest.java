package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Eliminar del finiquito un ítem que viene de un item programado anula ese programado. */
class LiquidacionFinalEliminarItemProgramadoTest {

    private LiquidacionFinalRepository repository;
    private LiquidacionFinalItemRepository itemRepository;
    private LiquidacionItemProgramadoService programadoService;
    private LiquidacionFinalService service;

    private LiquidacionFinal finiquito;
    private final List<LiquidacionFinalItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(LiquidacionFinalRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        itemRepository = mock(LiquidacionFinalItemRepository.class);
        when(itemRepository.findById(anyLong())).thenAnswer(i ->
                items.stream().filter(it -> it.getId().equals(i.getArgument(0))).findFirst());
        when(itemRepository.findByLiquidacionFinalIdOrderByIdAsc(anyLong())).thenAnswer(i -> new ArrayList<>(items));
        doAnswer(i -> { items.removeIf(it -> it.getId().equals(i.getArgument(0))); return null; })
                .when(itemRepository).deleteById(anyLong());
        programadoService = mock(LiquidacionItemProgramadoService.class);

        service = new LiquidacionFinalService(
                repository, mock(PagoSolicitudDetalleRepository.class), itemRepository,
                mock(FuncionarioService.class), mock(LiquidacionSueldoRepository.class), mock(VacacionRepository.class),
                mock(ConfiguracionRrhhService.class), mock(CajaVirtualService.class),
                mock(MovimientoCajaVirtualService.class), mock(MonedaService.class), mock(UsuarioService.class),
                mock(ValeRepository.class), mock(PrestamoRepository.class), mock(PrestamoCuotaRepository.class),
                mock(ClienteService.class), mock(VentaCreditoRepository.class), mock(PenalizacionRepository.class),
                mock(CreditoConvenioService.class), mock(AguinaldoRepository.class),
                mock(BaseRemunerativaService.class), mock(PrestamoCuotaDescuentoService.class),
                mock(ValeCuotaDescuentoService.class), mock(ItemProgramadoAplicacionService.class),
                programadoService);

        finiquito = new LiquidacionFinal();
        finiquito.setId(33L);
        finiquito.setEstado(LiquidacionFinalEstado.BORRADOR);
        finiquito.setTotalLiquidado(new BigDecimal("-2082000"));
        when(repository.lockById(33L)).thenReturn(Optional.of(finiquito));

        items.add(item(285L, "1522000", null, null));
        items.add(item(286L, "500000", "ADELANTO", 397L));
        items.add(item(287L, "60000", LiquidacionItemProgramado.REFERENCIA_TIPO, 25L));
    }

    private LiquidacionFinalItem item(Long id, String monto, String refTipo, Long refId) {
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setId(id);
        it.setLiquidacionFinal(finiquito);
        it.setTipo(LiquidacionItemTipo.DESCUENTO);
        it.setMonto(new BigDecimal(monto));
        it.setReferenciaTipo(refTipo);
        it.setReferenciaId(refId);
        return it;
    }

    @Test
    void programadoEnBorrador_borraAnulaElProgramadoYRecalcula() {
        assertTrue(service.eliminarItem(287L));

        InOrder orden = inOrder(repository, itemRepository, programadoService);
        orden.verify(repository).lockById(33L);
        orden.verify(itemRepository).deleteById(287L);
        orden.verify(itemRepository).flush();
        orden.verify(programadoService).anular(25L);
        assertEquals(0, new BigDecimal("-2022000").compareTo(finiquito.getTotalLiquidado()));
    }

    @Test
    void itemComun_borraYRecalculaSinAnularNada() {
        assertTrue(service.eliminarItem(286L));

        verify(itemRepository).deleteById(286L);
        verifyNoInteractions(programadoService);
        assertEquals(0, new BigDecimal("-1582000").compareTo(finiquito.getTotalLiquidado()));
    }

    @Test
    void finiquitoAprobado_rechazaSinBorrarNiAnular() {
        finiquito.setEstado(LiquidacionFinalEstado.APROBADA);

        assertThrows(GraphQLException.class, () -> service.eliminarItem(287L));
        verify(itemRepository, never()).deleteById(anyLong());
        verifyNoInteractions(programadoService);
    }

    @Test
    void siAnularRechaza_elMensajeDiceQueNoSeElimino() {
        when(programadoService.anular(25L)).thenThrow(new GraphQLException("El item programado ya se aplico en la liquidacion #9"));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.eliminarItem(287L));
        assertTrue(e.getMessage().startsWith("No se pudo eliminar el item: El item programado ya se aplico"), e.getMessage());
    }

    @Test
    void programadoSinReferencia_rechazaSinBorrar() {
        items.get(2).setReferenciaId(null);

        assertThrows(GraphQLException.class, () -> service.eliminarItem(287L));
        verify(itemRepository, never()).deleteById(anyLong());
        verifyNoInteractions(programadoService);
    }

    @Test
    void itemInexistente_devuelveFalse() {
        assertFalse(service.eliminarItem(999L));
        verify(itemRepository, never()).deleteById(anyLong());
    }

    @Test
    void aprobar_leeElFiniquitoConLock() {
        finiquito.setTotalLiquidado(new BigDecimal("100"));

        assertEquals(LiquidacionFinalEstado.APROBADA, service.aprobar(33L, null).getEstado());
        verify(repository).lockById(33L);
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void esItemProgramado_distingueElOrigen() {
        assertTrue(service.esItemProgramado(287L));
        assertFalse(service.esItemProgramado(286L));
        assertFalse(service.esItemProgramado(999L));
    }
}
