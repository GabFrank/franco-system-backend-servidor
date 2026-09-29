package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PagoSolicitudDetalle;
import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.*;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Anular una liquidacion pagada desde el hub de tesoreria. Antes, {@code anular} la marcaba ANULADA sin
 * devolver un pago bancario ni revertir vales/cuotas, y en efectivo dejaba el pago vivo (doble reversa
 * al anularlo despues). Ahora se anula junto con su pago, y un pago de lote se rechaza.
 *
 * <p>La liquidacion mensual usa el {@link LiquidacionSueldoService} real: lo que hay que probar es que la
 * misma instancia que el motor de pago pasa a APROBADA es la que {@code anular} termina de ANULAR.</p>
 */
class AnulacionPagoRrhhServiceTest {

    private static final long LIQ_ID = 486L;
    private static final long SOLICITUD = 88L;

    private LiquidacionSueldoRepository liquidacionRepository;
    private LiquidacionFinalRepository finiquitoRepository;
    private LiquidacionFinalService finiquitoService;
    private PagoProveedorService motor;
    private PagoService pagoService;
    private PagoSolicitudDetalleRepository detalleRepository;
    private TesoreriaSecurityService tesoreriaSecurity;
    private AnulacionPagoRrhhService service;

    private LiquidacionSueldo liq;
    private final List<PagoSolicitudDetalle> detalles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        liquidacionRepository = mock(LiquidacionSueldoRepository.class);
        when(liquidacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        LiquidacionItemRepository itemRepository = mock(LiquidacionItemRepository.class);
        LiquidacionSueldoService liquidacionService = new LiquidacionSueldoService(
                liquidacionRepository, itemRepository,
                mock(FuncionarioService.class), mock(MonedaService.class), mock(ConfiguracionRrhhService.class),
                mock(HoraExtraRepository.class), mock(PenalizacionRepository.class), mock(JustificativoRepository.class),
                mock(ValeRepository.class), mock(BonoRepository.class), mock(AguinaldoRepository.class),
                mock(VacacionRepository.class), mock(VacacionVentaRepository.class), mock(PrestamoRepository.class),
                mock(PrestamoCuotaRepository.class), mock(CajaVirtualService.class),
                mock(MovimientoCajaVirtualService.class), mock(UsuarioService.class),
                mock(PagoSolicitudDetalleRepository.class), mock(JornadaService.class),
                mock(CreditoConvenioService.class), mock(LiquidacionConceptoService.class),
                mock(PlatformTransactionManager.class), mock(PrestamoCuotaDescuentoService.class),
                mock(javax.persistence.EntityManager.class));

        finiquitoRepository = mock(LiquidacionFinalRepository.class);
        finiquitoService = mock(LiquidacionFinalService.class);
        motor = mock(PagoProveedorService.class);
        pagoService = mock(PagoService.class);
        detalleRepository = mock(PagoSolicitudDetalleRepository.class);
        tesoreriaSecurity = mock(TesoreriaSecurityService.class);
        service = new AnulacionPagoRrhhService(motor, pagoService, detalleRepository, liquidacionService,
                finiquitoService, liquidacionRepository, finiquitoRepository, mock(ValeRepository.class),
                mock(AguinaldoRepository.class), tesoreriaSecurity);

        // Liquidacion pagada desde el hub por banco: sin caja linkeada.
        liq = new LiquidacionSueldo();
        liq.setId(LIQ_ID);
        liq.setEstado(LiquidacionSueldoEstado.PAGADA);
        liq.setSolicitudPagoId(SOLICITUD);
        when(liquidacionRepository.lockById(LIQ_ID)).thenReturn(Optional.of(liq));
        when(liquidacionRepository.findById(LIQ_ID)).thenReturn(Optional.of(liq));

        when(detalleRepository.findBySolicitudPagoIdOrderByCreadoEnAsc(anyLong())).thenAnswer(i -> {
            List<PagoSolicitudDetalle> out = new ArrayList<>();
            for (PagoSolicitudDetalle d : detalles) if (d.getSolicitudPagoId().equals(i.getArgument(0))) out.add(d);
            return out;
        });
        when(detalleRepository.findByPagoIdOrderByCreadoEnAsc(anyLong())).thenAnswer(i -> {
            List<PagoSolicitudDetalle> out = new ArrayList<>();
            for (PagoSolicitudDetalle d : detalles) if (d.getPagoId().equals(i.getArgument(0))) out.add(d);
            return out;
        });
        when(pagoService.findById(anyLong())).thenAnswer(i -> Optional.of(pago(i.getArgument(0), PagoEstado.CONCLUIDO)));

        // El motor real, al anular el pago, sincroniza la liquidacion: la MISMA instancia vuelve a APROBADA.
        when(motor.anularPagoCpp(anyLong(), anyString(), any())).thenAnswer(i -> {
            liq.setEstado(LiquidacionSueldoEstado.APROBADA);
            return null;
        });
    }

    private static Pago pago(long id, PagoEstado estado) {
        Pago p = new Pago();
        p.setId(id);
        p.setEstado(estado);
        return p;
    }

    private void detalle(long pagoId, long solicitud, boolean anulado) {
        PagoSolicitudDetalle d = new PagoSolicitudDetalle();
        d.setPagoId(pagoId);
        d.setSolicitudPagoId(solicitud);
        d.setAnulado(anulado);
        detalles.add(d);
    }

    // ─────────────────────────── la causa ───────────────────────────

    @Test
    void anularDirectoUnaLiquidacionPagadaDesdeTesoreriaSeRechaza() {
        // Con el codigo viejo quedaba ANULADA sin devolver el pago bancario ni revertir los vales.
        LiquidacionSueldoService real = realDe();

        assertThrows(GraphQLException.class, () -> real.anular(LIQ_ID));
        assertEquals(LiquidacionSueldoEstado.PAGADA, liq.getEstado());
    }

    private LiquidacionSueldoService realDe() {
        return (LiquidacionSueldoService) org.springframework.test.util.ReflectionTestUtils
                .getField(service, "liquidacionSueldoService");
    }

    // ─────────────────────────── anular con su pago ───────────────────────────

    @Test
    void unPagoExclusivoSeAnulaYLaLiquidacionQuedaAnulada() {
        detalle(700L, SOLICITUD, false);

        LiquidacionSueldo res = service.anularLiquidacion(LIQ_ID);

        verify(motor).anularPagoCpp(eq(700L), contains("#" + LIQ_ID), any());
        verify(tesoreriaSecurity).requirePagarCpp();
        assertSame(liq, res);
        assertEquals(LiquidacionSueldoEstado.ANULADA, liq.getEstado());
    }

    @Test
    void unPagoParcialEnDosEventosAnulaLosDos() {
        detalle(700L, SOLICITUD, false);
        detalle(701L, SOLICITUD, false);

        service.anularLiquidacion(LIQ_ID);

        verify(motor).anularPagoCpp(eq(700L), anyString(), any());
        verify(motor).anularPagoCpp(eq(701L), anyString(), any());
        assertEquals(LiquidacionSueldoEstado.ANULADA, liq.getEstado());
    }

    @Test
    void unPagoDeLoteSeRechazaSinTocarNada() {
        detalle(700L, SOLICITUD, false);
        detalle(700L, 99L, false);          // el mismo evento pago otra obligacion
        LiquidacionSueldo otra = new LiquidacionSueldo();
        otra.setId(487L);
        when(liquidacionRepository.findBySolicitudPagoId(99L)).thenReturn(otra);

        GraphQLException ex = assertThrows(GraphQLException.class, () -> service.anularLiquidacion(LIQ_ID));

        assertTrue(ex.getMessage().contains("#487"), ex.getMessage());
        verify(motor, never()).anularPagoCpp(anyLong(), anyString(), any());
        assertEquals(LiquidacionSueldoEstado.PAGADA, liq.getEstado());
    }

    @Test
    void unDetalleAnuladoDeOtraSolicitudNoHaceDelPagoUnLote() {
        detalle(700L, SOLICITUD, false);
        detalle(700L, 99L, true);

        service.anularLiquidacion(LIQ_ID);

        verify(motor).anularPagoCpp(eq(700L), anyString(), any());
        assertEquals(LiquidacionSueldoEstado.ANULADA, liq.getEstado());
    }

    @Test
    void unPagoCanceladoSeDescarta() {
        detalle(700L, SOLICITUD, false);
        detalle(701L, SOLICITUD, false);
        when(pagoService.findById(700L)).thenReturn(Optional.of(pago(700L, PagoEstado.CANCELADO)));

        service.anularLiquidacion(LIQ_ID);

        verify(motor, never()).anularPagoCpp(eq(700L), anyString(), any());
        verify(motor).anularPagoCpp(eq(701L), anyString(), any());
    }

    @Test
    void sinPagosVivosRevierteEfectosYAnulaSinTocarTesoreria() {
        // PAGADA pero con todos sus detalles anulados: sin esto quedaba trabada (el guard rechaza y no
        // hay pago que anular).
        detalle(700L, SOLICITUD, true);

        service.anularLiquidacion(LIQ_ID);

        verify(motor, never()).anularPagoCpp(anyLong(), anyString(), any());
        verify(tesoreriaSecurity, never()).requirePagarCpp();
        assertEquals(LiquidacionSueldoEstado.ANULADA, liq.getEstado());
    }

    @Test
    void sinRolDeTesoreriaNoSeAnulaElPago() {
        detalle(700L, SOLICITUD, false);
        doThrow(new GraphQLException("No autorizado")).when(tesoreriaSecurity).requirePagarCpp();

        assertThrows(GraphQLException.class, () -> service.anularLiquidacion(LIQ_ID));
        verify(motor, never()).anularPagoCpp(anyLong(), anyString(), any());
        assertEquals(LiquidacionSueldoEstado.PAGADA, liq.getEstado());
    }

    @Test
    void pagadaContraCajaSinTesoreriaSeAnulaComoAntes() {
        liq.setEstado(LiquidacionSueldoEstado.APROBADA);
        liq.setSolicitudPagoId(null);

        service.anularLiquidacion(LIQ_ID);

        verify(tesoreriaSecurity, never()).requirePagarCpp();
        verify(motor, never()).anularPagoCpp(anyLong(), anyString(), any());
        assertEquals(LiquidacionSueldoEstado.ANULADA, liq.getEstado());
    }

    // ─────────────────────────── finiquito ───────────────────────────

    private LiquidacionFinal finiquitoPagadoDesdeTesoreria() {
        LiquidacionFinal lf = new LiquidacionFinal();
        lf.setId(900L);
        lf.setEstado(LiquidacionFinalEstado.PAGADA);
        lf.setSolicitudPagoId(77L);
        when(finiquitoRepository.lockById(900L)).thenReturn(Optional.of(lf));
        return lf;
    }

    @Test
    void elFiniquitoTambienSeAnulaConSuPago() {
        finiquitoPagadoDesdeTesoreria();
        detalle(800L, 77L, false);

        service.anularFiniquito(900L);

        verify(motor).anularPagoCpp(eq(800L), contains("#900"), any());
        verify(finiquitoService).anular(900L);
    }

    @Test
    void elFiniquitoSinPagoVivoRevierteSinTesoreria() {
        finiquitoPagadoDesdeTesoreria();

        service.anularFiniquito(900L);

        verify(finiquitoService).anularSinPagoVivo(900L);
        verify(motor, never()).anularPagoCpp(anyLong(), anyString(), any());
    }
}
