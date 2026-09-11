package com.franco.dev.service.rrhh;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaVirtualService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.TesoreriaService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Liquidacion con neto negativo: el funcionario le debe a la empresa (descuentos &gt;
 * haberes), asi que no hay nada que pagarle.
 *
 * <p>Usa el {@link TesoreriaService} REAL (solo con los repositorios mockeados) en vez de
 * un mock de {@link MovimientoCajaVirtualService}, porque lo que hay que probar es el
 * efecto sobre el saldo de la caja, no que se haya llamado a tal metodo. Con un mock de la
 * fachada el test asertaria sobre el mock y pasaria igual con el contra-asiento mal
 * firmado, que es justamente el bug.</p>
 */
class LiquidacionSueldoNetoNegativoTest {

    private static final Long CAJA_ID = 1L;
    private static final Long MONEDA_ID = 10L;
    private static final BigDecimal SALDO_INICIAL = new BigDecimal("5000000");

    private LiquidacionSueldoRepository repository;
    private LiquidacionItemRepository itemRepository;
    private LiquidacionSueldoService service;
    private MovimientoCajaVirtualService movimientoCajaVirtualService;

    private CajaVirtual caja;
    private Moneda gs;
    private CajaVirtualSaldo saldo;
    private LiquidacionSueldo liq;

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

        // --- Tesoreria real, repositorios mockeados ---
        CajaVirtualSaldoRepository saldoRepository = mock(CajaVirtualSaldoRepository.class);
        CajaVirtualRepository cajaVirtualRepository = mock(CajaVirtualRepository.class);
        MonedaRepository monedaRepository = mock(MonedaRepository.class);
        MovimientoCajaVirtualRepository movimientoRepository = mock(MovimientoCajaVirtualRepository.class);
        com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository configRepository =
                mock(com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository.class);
        when(configRepository.findAll()).thenReturn(Collections.emptyList());

        when(cajaVirtualRepository.findById(CAJA_ID)).thenReturn(Optional.of(caja));
        when(cajaVirtualRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(saldoRepository.lockByCajaVirtualIdAndMonedaId(CAJA_ID, MONEDA_ID)).thenReturn(Optional.of(saldo));
        when(saldoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // Los movimientos posteados quedan recuperables por id: la anulacion busca el original.
        AtomicLong secuencia = new AtomicLong(100L);
        when(movimientoRepository.save(any())).thenAnswer(i -> {
            MovimientoCajaVirtual m = i.getArgument(0);
            if (m.getId() == null) m.setId(secuencia.incrementAndGet());
            when(movimientoRepository.findById(m.getId())).thenReturn(Optional.of(m));
            return m;
        });

        TesoreriaService tesoreria = new TesoreriaService(saldoRepository, mock(TesoreriaSecurityService.class),
                cajaVirtualRepository, monedaRepository, movimientoRepository, configRepository);
        movimientoCajaVirtualService =
                new MovimientoCajaVirtualService(movimientoRepository, tesoreria);

        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        when(cajaVirtualService.findById(CAJA_ID)).thenReturn(Optional.of(caja));

        repository = mock(LiquidacionSueldoRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        itemRepository = mock(LiquidacionItemRepository.class);
        when(itemRepository.findByLiquidacionIdOrderByIdAsc(any())).thenReturn(Collections.emptyList());

        service = new LiquidacionSueldoService(
                repository,
                itemRepository,
                mock(FuncionarioService.class),
                mock(MonedaService.class),
                mock(ConfiguracionRrhhService.class),
                mock(HoraExtraRepository.class),
                mock(PenalizacionRepository.class),
                mock(JustificativoRepository.class),
                mock(ValeRepository.class),
                mock(BonoRepository.class),
                mock(AguinaldoRepository.class),
                mock(VacacionRepository.class),
                mock(VacacionVentaRepository.class),
                mock(PrestamoRepository.class),
                mock(PrestamoCuotaRepository.class),
                cajaVirtualService,
                movimientoCajaVirtualService,
                mock(UsuarioService.class),
                mock(com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository.class),
                mock(JornadaService.class),
                mock(CreditoConvenioService.class),
                mock(LiquidacionConceptoService.class),
                mock(PlatformTransactionManager.class),
                mock(javax.persistence.EntityManager.class));

        liq = new LiquidacionSueldo();
        liq.setId(486L);
        liq.setPeriodo("2026-09");
        liq.setMoneda(gs);
        liq.setEstado(LiquidacionSueldoEstado.APROBADA);
        liq.setTotalHaberes(new BigDecimal("8"));
        liq.setTotalDescuentos(new BigDecimal("1000008"));
        liq.setTotalNeto(new BigDecimal("-1000000"));
        when(repository.findById(486L)).thenReturn(Optional.of(liq));
    }

    /**
     * Un neto negativo significa que el funcionario le debe a la empresa. Pagarlo saca esa
     * plata de la caja al reves: el egreso se postea con la cantidad negada y termina
     * descontando lo que en realidad habria que cobrar.
     */
    @Test
    void noSePuedePagarUnaLiquidacionConNetoNegativo() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.pagar(486L, CAJA_ID));
        assertTrue(e.getMessage().toLowerCase().contains("neto negativo"),
                "el mensaje tiene que explicar por que no se paga, dijo: " + e.getMessage());
        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "no tenia que moverse plata, la caja quedo en " + saldo.getSaldo());
        assertEquals(LiquidacionSueldoEstado.APROBADA, liq.getEstado(),
                "la liquidacion no tenia que quedar pagada");
    }

    /**
     * El caso que se escapo a produccion: liquidaciones con neto negativo que ya se pagaron
     * antes de la validacion. Anularlas tiene que devolver la plata, no volver a sacarla.
     */
    @Test
    void anularUnPagoConNetoNegativoDevuelveLaPlataALaCaja() {
        MovimientoCajaVirtual pago = pagoYaPosteado();
        BigDecimal saldoTrasPago = saldo.getSaldo();

        service.anular(486L);

        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "la anulacion tenia que dejar la caja como antes del pago (" + SALDO_INICIAL
                        + "), quedo en " + saldo.getSaldo() + " partiendo de " + saldoTrasPago);
        assertEquals(LiquidacionSueldoEstado.ANULADA, liq.getEstado());
        assertFalse(Boolean.TRUE.equals(pago.getActivo()),
                "el movimiento original tenia que quedar inactivo para que la UI lo tache");
    }

    /** Una liquidacion normal se sigue pagando y anulando sin dejar rastro en el saldo. */
    @Test
    void anularUnPagoConNetoPositivoSigueDevolviendoLaPlata() {
        liq.setTotalHaberes(new BigDecimal("1500000"));
        liq.setTotalDescuentos(BigDecimal.ZERO);
        liq.setTotalNeto(new BigDecimal("1500000"));

        service.pagar(486L, CAJA_ID);
        assertEquals(0, new BigDecimal("3500000").compareTo(saldo.getSaldo()),
                "el pago tenia que sacar 1.500.000, la caja quedo en " + saldo.getSaldo());

        service.anular(486L);
        assertEquals(0, SALDO_INICIAL.compareTo(saldo.getSaldo()),
                "la anulacion tenia que devolver los 1.500.000, la caja quedo en " + saldo.getSaldo());
    }

    /**
     * Deja la liquidacion como la dejaba el pago antes de la validacion: PAGADA, con el
     * egreso de neto negativo ya posteado en la caja. Postea por el mismo camino que usaba
     * {@code pagar()} para que el saldo quede exactamente igual que en produccion.
     */
    private MovimientoCajaVirtual pagoYaPosteado() {
        MovimientoCajaVirtual mov = new MovimientoCajaVirtual();
        mov.setCajaVirtual(caja);
        mov.setTipoMovimiento(CajaVirtualTipoMovimiento.EGRESO);
        mov.setCantidad(liq.getTotalNeto().doubleValue());
        mov.setMoneda(gs);
        mov.setReferenciaId(liq.getId());
        mov.setOrigenTipo(OrigenMovimientoTipo.RRHH_LIQUIDACION_SUELDO);
        mov.setOrigenId(liq.getId());
        mov.setDescripcion("PAGO SALARIO " + liq.getPeriodo() + " - LIQ #" + liq.getId());
        mov.setActivo(true);
        MovimientoCajaVirtual posteado = movimientoCajaVirtualService.registrarMovimiento(mov);

        liq.setEstado(LiquidacionSueldoEstado.PAGADA);
        liq.setCajaVirtualId(CAJA_ID);
        liq.setMovimientoCajaVirtualId(posteado.getId());
        return posteado;
    }
}
