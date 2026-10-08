package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoNotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.CambioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El monto de una solicitud de pago no puede tener mas decimales que su moneda: SP-001338 (bodega) nacio con
 * 899854.5 guaranies y no habia pago que la saldara, porque el motor rechaza medio guarani de exceso y el
 * dialogo no deja confirmar con uno de faltante.
 */
class SolicitudPagoServiceMontoRedondeoTest {

    private SolicitudPagoRepository repository;
    private SolicitudPagoNotaRecepcionRepository relacionRepository;
    private NotaRecepcionRepository notaRepository;
    private SolicitudPagoNotaRecepcionService relacionService;
    private SolicitudPagoService service;
    private Moneda guarani;
    private Moneda dolar;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        repository = mock(SolicitudPagoRepository.class);
        relacionRepository = mock(SolicitudPagoNotaRecepcionRepository.class);
        notaRepository = mock(NotaRecepcionRepository.class);
        relacionService = new SolicitudPagoNotaRecepcionService(relacionRepository, repository, notaRepository);
        service = new SolicitudPagoService(repository, relacionService, notaRepository,
                mock(ProcesoEtapaService.class), mock(RecepcionMercaderiaNotaService.class),
                mock(RecepcionMercaderiaService.class), mock(MonedaRepository.class),
                mock(FormaPagoRepository.class), mock(CambioService.class));

        guarani = moneda(1L, "GUARANI", 0);
        dolar = moneda(3L, "DOLAR", 2);
        proveedor = new Proveedor();
        proveedor.setId(3L);

        when(repository.save(any())).thenAnswer(i -> {
            SolicitudPago s = i.getArgument(0);
            if (s.getId() == null) s.setId(1343L);
            when(repository.findById(1343L)).thenReturn(Optional.of(s));
            return s;
        });
        when(relacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Moneda moneda(Long id, String denominacion, Integer decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setDecimales(decimales);
        return m;
    }

    private NotaRecepcion nota(Long id, Moneda moneda, double valor) {
        NotaRecepcion n = new NotaRecepcion();
        n.setId(id);
        n.setMoneda(moneda);
        when(notaRepository.findById(id)).thenReturn(Optional.of(n));
        when(notaRepository.valorTotalConRechazos(id)).thenReturn(valor);
        return n;
    }

    private SolicitudPago crear(Moneda moneda, NotaRecepcion... notas) {
        List<NotaRecepcion> lista = Arrays.asList(notas);
        when(notaRepository.findAllById(any())).thenReturn(lista);
        Long[] ids = lista.stream().map(NotaRecepcion::getId).toArray(Long[]::new);
        return service.crearSolicitudPago(proveedor, Arrays.asList(ids), moneda, null, null, null, new Usuario());
    }

    private List<SolicitudPagoNotaRecepcion> relacionesGuardadas(int cantidad) {
        ArgumentCaptor<SolicitudPagoNotaRecepcion> c = ArgumentCaptor.forClass(SolicitudPagoNotaRecepcion.class);
        verify(relacionRepository, times(cantidad)).save(c.capture());
        return c.getAllValues();
    }

    /** El caso de SP-001338: una factura cuyos items suman medio guarani. */
    @Test
    void unaNotaEnGuaraniesConMedioGuaraniNaceRedondeada() {
        SolicitudPago sp = crear(guarani, nota(3954L, guarani, 899854.5));

        assertEquals(899855.0, sp.getMontoTotal());
        assertEquals(899855.0, relacionesGuardadas(1).get(0).getMontoIncluido());
    }

    /** El total es la suma de lo incluido por nota, no el redondeo de la suma cruda (que daria 21). */
    @Test
    void elTotalEsLaSumaDeLasNotasYaRedondeadas() {
        SolicitudPago sp = crear(guarani, nota(1L, guarani, 10.5), nota(2L, guarani, 10.5));

        assertEquals(22.0, sp.getMontoTotal());
        relacionesGuardadas(2).forEach(r -> assertEquals(11.0, r.getMontoIncluido()));
    }

    @Test
    void unaMonedaConCentavosConservaDosDecimales() {
        SolicitudPago sp = crear(dolar, nota(1L, dolar, 10.005));
        assertEquals(10.01, sp.getMontoTotal());
    }

    /** 100.10 + 200.20 en double da 300.29999999999995. */
    @Test
    void laSumaConCentavosNoDejaRuidoDeDouble() {
        SolicitudPago sp = crear(dolar, nota(1L, dolar, 100.10), nota(2L, dolar, 200.20));
        assertEquals(300.30, sp.getMontoTotal());
    }

    @Test
    void unaNotaEnDolaresConCabeceraEnGuaraniesQuedaEntera() {
        NotaRecepcion n = nota(1L, dolar, 123.45);
        n.setCotizacion(7310.5);

        SolicitudPago sp = crear(guarani, n);

        // 123.45 * 7310.5 = 902481.225
        assertEquals(902481.0, sp.getMontoTotal());
        assertEquals(902481.0, relacionesGuardadas(1).get(0).getMontoIncluido());
    }

    @Test
    void sinDecimalesCargadosElGuaraniVaEnteroYElRestoConCentavos() {
        assertEquals(11.0, SolicitudPagoNotaRecepcionService.redondearAMoneda(10.5, moneda(1L, "GUARANI", null)));
        assertEquals(10.57, SolicitudPagoNotaRecepcionService.redondearAMoneda(10.567, moneda(2L, "REAL", null)));
        assertEquals(10.567, SolicitudPagoNotaRecepcionService.redondearAMoneda(10.567, null));
    }

    /** La mutation agregarNotaASolicitudPago manda el monto desde el cliente: no pasa por el calculo del servicio. */
    @Test
    void unMontoQueLlegaDelClienteTambienSeRedondea() {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(7L);
        sp.setMoneda(guarani);
        when(repository.findById(7L)).thenReturn(Optional.of(sp));
        nota(9L, guarani, 0);

        SolicitudPagoNotaRecepcion r = relacionService.agregarNotaASolicitud(7L, 9L, 509140.5);

        assertEquals(509141.0, r.getMontoIncluido());
    }

    @Test
    void elRecalculoDelTotalRedondeaLaSuma() {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(7L);
        sp.setMoneda(dolar);
        when(repository.findById(7L)).thenReturn(Optional.of(sp));
        when(relacionRepository.getTotalMontoForSolicitudPago(7L)).thenReturn(100.10 + 200.20);

        relacionService.recalcularMontoTotalSolicitud(7L);

        assertEquals(300.30, sp.getMontoTotal());
    }
}
