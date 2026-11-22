package com.franco.dev.graphql.operaciones;

import com.franco.dev.graphql.productos.CostoPorProductoGraphQL;
import com.franco.dev.repository.operaciones.PagoDetalleCuotaRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.PagoDetalleCuotaService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tanda de compras y recepcion de #674: cada consulta paginada termina su orden en una clave
 * unica. Corre en CI, sin base; el recorrido real de las paginas esta en
 * {@code com.franco.dev.repository.operaciones.OrdenTotalComprasIT}.
 */
class OrdenTotalComprasTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "com.franco.dev.repository.operaciones.NotaRecepcionItemRepository | findItemsByNotaIdPaginados     | ORDER BY p.descripcion ASC, nri.id ASC",
            "com.franco.dev.repository.operaciones.NotaRecepcionItemRepository | findUltimasComprasByProductoId | ORDER BY nri.creadoEn DESC, nri.id DESC",
            "com.franco.dev.repository.productos.CostosPorProductoRepository   | findLastByProductoId           | ORDER BY cps.creadoEn DESC, cps.id DESC",
            "com.franco.dev.repository.operaciones.SolicitudPagoRepository     | findByPedidoIdPaginated        | ORDER BY sp.fechaSolicitud DESC, sp.id DESC",
            "com.franco.dev.repository.operaciones.SolicitudPagoRepository     | findByProveedorIdAndEstado     | ORDER BY sp.fechaSolicitud DESC, sp.id DESC",
            "com.franco.dev.repository.operaciones.ProcesoEtapaRepository      | findByEstadoEtapa              | ORDER BY pe.creadoEn DESC, pe.id DESC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName(repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertEquals(1, consultas.size(), metodo);
        assertTrue(consultas.get(0).endsWith(final_), metodo + ": " + consultas.get(0));
    }

    /** Los resolvers que llaman a una consulta sin ORDER BY tienen que pasar un Sort que termine en id. */
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @org.junit.jupiter.api.Nested
    class Resolvers {

        @Mock NotaRecepcionItemService notaItemService;
        @InjectMocks NotaRecepcionItemGraphQL notaItemGraphQL;

        @Mock NotaRecepcionService notaService;
        @InjectMocks NotaRecepcionGraphQL notaGraphQL;

        @Mock CostosPorProductoService costoService;
        @InjectMocks CostoPorProductoGraphQL costoGraphQL;

        @Mock SolicitudPagoService solicitudService;
        @Mock SolicitudPagoRepository solicitudRepository;
        @InjectMocks SolicitudPagoGraphQL solicitudGraphQL;

        @Mock PagoDetalleCuotaRepository cuotaRepository;

        private final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        @Test
        void itemsDeNotaPorEstadoOrdenanPorId() {
            for (FiltroVerificacion f : Arrays.asList(FiltroVerificacion.PENDIENTES, FiltroVerificacion.VERIFICADOS,
                    FiltroVerificacion.RECHAZADOS)) {
                notaItemGraphQL.notaRecepcionItemListPorNotaRecepcionIdYSucursales(1L, Collections.singletonList(1L), 0, 10, f, null);
            }
            verify(notaItemService).findByNotaRecepcionIdAndSucursalesIdsAndNoVerificados(anyLong(), anyList(), isNull(), pageable.capture());
            verify(notaItemService).findByNotaRecepcionIdAndSucursalesIdsAndVerificados(anyLong(), anyList(), isNull(), pageable.capture());
            verify(notaItemService).findByNotaRecepcionIdAndSucursalesIdsAndRechazados(anyLong(), anyList(), isNull(), pageable.capture());
            pageable.getAllValues().forEach(p -> assertEquals(Sort.by("id"), p.getSort()));
        }

        @Test
        void notasPorPedidoOrdenanPorIdDesc() {
            notaGraphQL.notaRecepcionPorPedidoIdAndNumero(1L, null, 0, 10);
            verify(notaService).findByPedidoId(eq(1L), pageable.capture());
            assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());
        }

        @Test
        void costosPorProductoOrdenanPorIdDesc() {
            costoGraphQL.costosPorProductoId(1L, 0, 10);
            verify(costoService).findByProductoId(eq(1L), pageable.capture());
            assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());
        }

        @Test
        @SuppressWarnings("unchecked")
        void solicitudesDePagoOrdenanPorIdDesc() {
            // El usuario tiene rol de compras: cualquier chequeo booleano responde que si.
            TesoreriaSecurityService permiso = mock(TesoreriaSecurityService.class,
                    inv -> inv.getMethod().getReturnType() == boolean.class ? Boolean.TRUE : null);
            ReflectionTestUtils.setField(solicitudGraphQL, "tesoreriaSecurityService", permiso);
            when(solicitudService.getRepository()).thenReturn(solicitudRepository);
            solicitudGraphQL.solicitudesPagoPaginated(0, 10, null, null, null, null, null, null);
            verify(solicitudRepository).findAll(any(Specification.class), pageable.capture());
            assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());
        }

        @Test
        @SuppressWarnings("unchecked")
        void cuotasDePagoOrdenanPorIdDesc() {
            new PagoDetalleCuotaService(cuotaRepository).findByFiltro(null, null, null, null, null, 0, 10);
            verify(cuotaRepository).findAll(any(Specification.class), pageable.capture());
            assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());
        }
    }
}
