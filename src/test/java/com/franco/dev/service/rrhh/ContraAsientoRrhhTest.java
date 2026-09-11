package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import com.franco.dev.repository.financiero.VentaCreditoRepository;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.TesoreriaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contra-asientos de los otros dos modulos de RRHH que mueven la Caja Mayor: finiquito y
 * vale. Los tres armaban el AJUSTE de anulacion a mano copiando el monto sin negarlo, que
 * solo revierte de casualidad cuando el monto es positivo.
 *
 * <p>Igual que {@link LiquidacionSueldoNetoNegativoTest}, usa la tesoreria REAL para poder
 * asertar sobre el saldo y no sobre un mock de la fachada.</p>
 */
class ContraAsientoRrhhTest {

    private static final Long CAJA_ID = 1L;
    private static final Long MONEDA_ID = 10L;
    private static final BigDecimal SALDO_INICIAL = new BigDecimal("5000000");

    private CajaVirtual caja;
    private Moneda gs;
    private CajaVirtualSaldo saldo;
    private MovimientoCajaVirtualService movimientoCajaVirtualService;
    private CajaVirtualService cajaVirtualService;

    private LiquidacionFinalRepository finiquitoRepository;
    private LiquidacionFinalService finiquitoService;
    private ValeRepository valeRepository;
    private ValeService valeService;

    @BeforeEach
    void setUp() {
        caja = new CajaVirtual();
        caja.setId(CAJA_ID);
        caja.setPermiteSaldoNegativo(false);

        gs = new Moneda();
        gs.setId(MONEDA_ID);
        gs.setDenominacion("GUARANIES");

        saldo = new CajaVirtualSaldo();
        saldo.setCajaVirtual(caja);
        saldo.setMoneda(gs);
        saldo.setSaldo(SALDO_INICIAL);

        CajaVirtualSaldoRepository saldoRepository = mock(CajaVirtualSaldoRepository.class);
        CajaVirtualRepository cajaVirtualRepository = mock(CajaVirtualRepository.class);
        MovimientoCajaVirtualRepository movimientoRepository = mock(MovimientoCajaVirtualRepository.class);
        com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository configRepository =
                mock(com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository.class);
        when(configRepository.findAll()).thenReturn(Collections.emptyList());

        when(cajaVirtualRepository.findById(CAJA_ID)).thenReturn(Optional.of(caja));
        when(cajaVirtualRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(saldoRepository.lockByCajaVirtualIdAndMonedaId(CAJA_ID, MONEDA_ID)).thenReturn(Optional.of(saldo));
        when(saldoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        AtomicLong secuencia = new AtomicLong(100L);
        when(movimientoRepository.save(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            if (m.getId() == null) m.setId(secuencia.incrementAndGet());
            when(movimientoRepository.findById(m.getId())).thenReturn(Optional.of(m));
            return m;
        });

        TesoreriaService tesoreria = new TesoreriaService(saldoRepository, mock(TesoreriaSecurityService.class),
                cajaVirtualRepository, mock(MonedaRepository.class), movimientoRepository, configRepository);
        movimientoCajaVirtualService = new MovimientoCajaVirtualService(movimientoRepository, tesoreria);

        cajaVirtualService = mock(CajaVirtualService.class);
        when(cajaVirtualService.findById(CAJA_ID)).thenReturn(Optional.of(caja));

        finiquitoRepository = mock(LiquidacionFinalRepository.class);
        when(finiquitoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        finiquitoService = new LiquidacionFinalService(
                finiquitoRepository,
                mock(PagoSolicitudDetalleRepository.class),
                mock(LiquidacionFinalItemRepository.class),
                mock(FuncionarioService.class),
                mock(LiquidacionSueldoRepository.class),
                mock(VacacionRepository.class),
                mock(ConfiguracionRrhhService.class),
                cajaVirtualService,
                movimientoCajaVirtualService,
                mock(MonedaService.class),
                mock(UsuarioService.class),
                mock(ValeRepository.class),
                mock(PrestamoRepository.class),
                mock(PrestamoCuotaRepository.class),
                mock(ClienteService.class),
                mock(VentaCreditoRepository.class),
                mock(PenalizacionRepository.class),
                mock(CreditoConvenioService.class),
                mock(AguinaldoRepository.class),
                mock(BaseRemunerativaService.class));

        valeRepository = mock(ValeRepository.class);
        when(valeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        valeService = new ValeService(
                valeRepository,
                cajaVirtualService,
                movimientoCajaVirtualService,
                mock(UsuarioService.class),
                mock(PagoSolicitudDetalleRepository.class));
    }

    // ─────────────────────────────── finiquito ───────────────────────────────

    /** Un finiquito cuyo total da negativo no se paga: el funcionario le debe a la empresa. */
    @Test
    void noSePuedePagarUnFiniquitoConTotalNegativo() {
        LiquidacionFinal lf = finiquito(new BigDecimal("-800000"), LiquidacionFinalEstado.APROBADA);

        GraphQLException e = assertThrows(GraphQLException.class, () -> finiquitoService.pagar(77L, CAJA_ID));
        assertTrue(e.getMessage().toLowerCase().contains("negativo"),
                "el mensaje tiene que explicar por que no se paga, dijo: " + e.getMessage());
        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "no tenia que moverse plata, la caja quedo en " + saldo.getSaldo());
        assertEquals(LiquidacionFinalEstado.APROBADA, lf.getEstado());
    }

    /** Finiquito ya pagado con total negativo (dato viejo): anularlo devuelve la plata. */
    @Test
    void anularUnFiniquitoConTotalNegativoDevuelveLaPlataALaCaja() {
        LiquidacionFinal lf = finiquito(new BigDecimal("-800000"), LiquidacionFinalEstado.PAGADA);
        MovimientoCajaVirtual pago = posteaEgreso(lf.getTotalLiquidado(),
                com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo.RRHH_LIQUIDACION_FINAL, lf.getId());
        lf.setCajaVirtualId(CAJA_ID);
        lf.setMovimientoCajaVirtualId(pago.getId());

        finiquitoService.anular(77L);

        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "la anulacion tenia que dejar la caja en " + SALDO_INICIAL + ", quedo en " + saldo.getSaldo());
        assertFalse(Boolean.TRUE.equals(pago.getActivo()),
                "el movimiento original tenia que quedar inactivo");
    }

    /** El finiquito normal se sigue anulando sin dejar rastro en el saldo. */
    @Test
    void anularUnFiniquitoNormalSigueDevolviendoLaPlata() {
        LiquidacionFinal lf = finiquito(new BigDecimal("2000000"), LiquidacionFinalEstado.PAGADA);
        MovimientoCajaVirtual pago = posteaEgreso(lf.getTotalLiquidado(),
                com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo.RRHH_LIQUIDACION_FINAL, lf.getId());
        lf.setCajaVirtualId(CAJA_ID);
        lf.setMovimientoCajaVirtualId(pago.getId());
        assertEquals(0, new BigDecimal("3000000").compareTo(saldo.getSaldo()));

        finiquitoService.anular(77L);

        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "la caja quedo en " + saldo.getSaldo());
    }

    // ───────────────────────────────── vale ──────────────────────────────────

    /** Anular un vale confirmado devuelve a la caja exactamente lo que salio. */
    @Test
    void anularUnValeDevuelveLaPlataALaCaja() {
        Vale vale = new Vale();
        vale.setId(31L);
        vale.setEstado(ValeEstado.CONFIRMADO);
        vale.setMonto(new BigDecimal("300000"));
        vale.setMoneda(gs);
        when(valeRepository.findById(31L)).thenReturn(Optional.of(vale));

        MovimientoCajaVirtual pago = posteaEgreso(vale.getMonto(),
                com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo.RRHH_VALE, vale.getId());
        vale.setCajaVirtualId(CAJA_ID);
        vale.setMovimientoCajaVirtualId(pago.getId());
        assertEquals(0, new BigDecimal("4700000").compareTo(saldo.getSaldo()));

        valeService.anular(31L);

        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "la caja quedo en " + saldo.getSaldo());
        assertEquals(ValeEstado.ANULADO, vale.getEstado());
        assertFalse(Boolean.TRUE.equals(pago.getActivo()),
                "el movimiento original tenia que quedar inactivo");
    }

    // ──────────────────────────────── helpers ────────────────────────────────

    private LiquidacionFinal finiquito(BigDecimal total, LiquidacionFinalEstado estado) {
        LiquidacionFinal lf = new LiquidacionFinal();
        lf.setId(77L);
        lf.setEstado(estado);
        lf.setTotalLiquidado(total);
        lf.setMoneda(gs);
        when(finiquitoRepository.findById(77L)).thenReturn(Optional.of(lf));
        return lf;
    }

    /** Postea el egreso del pago por el mismo camino que usan los servicios. */
    private MovimientoCajaVirtual posteaEgreso(BigDecimal monto,
                                               com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo origen,
                                               Long origenId) {
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(monto.doubleValue());
        mov.setMoneda(gs);
        mov.setReferenciaId(origenId);
        mov.setOrigenTipo(origen);
        mov.setOrigenId(origenId);
        mov.setActivo(true);
        return movimientoCajaVirtualService.registrarMovimiento(mov);
    }
}
