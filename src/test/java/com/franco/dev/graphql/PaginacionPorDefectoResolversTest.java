package com.franco.dev.graphql;

import com.franco.dev.domain.activos.Telemetria;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.graphql.activos.TelemetriaGraphQL;
import com.franco.dev.graphql.financiero.DocumentoElectronicoGraphQL;
import com.franco.dev.graphql.operaciones.MovimientoGraphQL;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.activos.TelemetriaService;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #582: consultas que recorrian la tabla entera o fallaban sin page/size.
 */
class PaginacionPorDefectoResolversTest {

    @Nested
    class DocumentosElectronicosPorEstado {

        @Mock private DocumentoElectronicoService service;
        @InjectMocks private DocumentoElectronicoGraphQL resolver;
        private AutoCloseable mocks;

        @BeforeEach void setUp() { mocks = MockitoAnnotations.openMocks(this); }
        @AfterEach void tearDown() throws Exception { mocks.close(); }

        @Test
        void devuelveSoloLaPaginaPedida() {
            DocumentoElectronico de = new DocumentoElectronico();
            when(service.findByFilters(eq(EstadoDE.APROBADO), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.singletonList(de)));

            List<DocumentoElectronico> res = resolver.documentoElectronicosPorEstado(EstadoDE.APROBADO, 2, 25);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(service).findByFilters(eq(EstadoDE.APROBADO), isNull(), isNull(), isNull(), pageable.capture());
            assertEquals(2, pageable.getValue().getPageNumber());
            assertEquals(25, pageable.getValue().getPageSize());
            assertEquals(Collections.singletonList(de), res);
            // ya no se trae la tabla entera
            verify(service, never()).findByEstado(any());
        }
    }

    @Nested
    class MovimientoStockPorFiltros {

        @Mock private MovimientoStockService service;
        @InjectMocks private MovimientoGraphQL resolver;
        private AutoCloseable mocks;

        @BeforeEach void setUp() { mocks = MockitoAnnotations.openMocks(this); }
        @AfterEach void tearDown() throws Exception { mocks.close(); }

        @Test
        void sinPageNiSizeDevuelveLaPrimeraPagina() {
            Page<?> vacia = Page.empty();
            when(service.findMovimientoStockWithFilters(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn((Page) vacia);

            Object res = resolver.findMovimientoStockByFilters("2026-11-01 00:00:00", "2026-11-13 23:59:59",
                    null, 7L, null, null, null, null);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(service).findMovimientoStockWithFilters(any(), any(), any(), eq(7L), any(), any(), pageable.capture());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(15, pageable.getValue().getPageSize());
            assertSame(vacia, res);
        }

        @Test
        void conPageYSizeLosRespeta() {
            when(service.findMovimientoStockWithFilters(any(), any(), any(), anyLong(), any(), any(), any()))
                    .thenReturn(Page.empty());

            resolver.findMovimientoStockByFilters("2026-11-01 00:00:00", "2026-11-13 23:59:59",
                    null, 7L, null, null, 3, 50);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(service).findMovimientoStockWithFilters(any(), any(), any(), eq(7L), any(), any(), pageable.capture());
            assertEquals(3, pageable.getValue().getPageNumber());
            assertEquals(50, pageable.getValue().getPageSize());
        }

        @Test
        void sinProductoSigueDevolviendoNull() {
            assertNull(resolver.findMovimientoStockByFilters("2026-11-01 00:00:00", "2026-11-13 23:59:59",
                    null, null, null, null, null, null));
        }
    }

    @Nested
    class TelemetriaPorDispositivo {

        @Mock private TelemetriaService service;
        @InjectMocks private TelemetriaGraphQL resolver;
        private AutoCloseable mocks;

        @BeforeEach void setUp() { mocks = MockitoAnnotations.openMocks(this); }
        @AfterEach void tearDown() throws Exception { mocks.close(); }

        @Test
        void sinFechasPaginaEnVezDeTraerTodo() {
            Telemetria t = new Telemetria();
            when(service.findByDispositivoIdAndFechaGpsBetween(eq(5L), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.singletonList(t)));

            List<Telemetria> res = resolver.telemetriaPorDispositivo(5L, null, null, 0, 10);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(service).findByDispositivoIdAndFechaGpsBetween(eq(5L), any(), any(), pageable.capture());
            assertEquals(10, pageable.getValue().getPageSize());
            assertEquals(Collections.singletonList(t), res);
            verify(service, never()).findByDispositivoId(anyLong());
        }

        @Test
        void soloFinNoMandaNullComoInicio() {
            when(service.findByDispositivoIdAndFechaGpsBetween(eq(5L), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            resolver.telemetriaPorDispositivo(5L, null, "2026-11-13 23:59:59", 0, 10);

            ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> fin = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(service).findByDispositivoIdAndFechaGpsBetween(eq(5L), inicio.capture(), fin.capture(), any());
            assertNotNull(inicio.getValue());
            assertEquals(LocalDateTime.of(2026, 11, 13, 23, 59, 59), fin.getValue());
        }

        @Test
        void soloInicioNoMandaNullComoFin() {
            when(service.findByDispositivoIdAndFechaGpsBetween(eq(5L), any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            resolver.telemetriaPorDispositivo(5L, "2026-11-01 00:00:00", null, 0, 10);

            ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> fin = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(service).findByDispositivoIdAndFechaGpsBetween(eq(5L), inicio.capture(), fin.capture(), any());
            assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0, 0), inicio.getValue());
            assertNotNull(fin.getValue());
        }
    }

    @Nested
    class CajasPorUsuario {

        @Mock private PdvCajaRepository repository;
        @InjectMocks private PdvCajaService service;
        private AutoCloseable mocks;

        @BeforeEach void setUp() { mocks = MockitoAnnotations.openMocks(this); }
        @AfterEach void tearDown() throws Exception { mocks.close(); }

        @Test
        void sinPageNiSizeNoFalla() {
            List<PdvCaja> cajas = Collections.singletonList(new PdvCaja());
            when(repository.findByUsuarioIdOrderByIdDesc(eq(9L), any(Pageable.class))).thenReturn(cajas);

            assertSame(cajas, service.findByUsuarioId(9L, null, null));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findByUsuarioIdOrderByIdDesc(eq(9L), pageable.capture());
            assertEquals(0, pageable.getValue().getPageNumber());
            assertEquals(15, pageable.getValue().getPageSize());
        }
    }
}
