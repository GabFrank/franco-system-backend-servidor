package com.franco.dev.repository.financiero;

import com.franco.dev.graphql.financiero.CajaVirtualGraphQL;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import org.junit.jupiter.api.Nested;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tanda de caja y tesoreria de #674. Retiros, gastos, cajas PDV y ventas a credito/tarjeta tienen
 * PK (id, sucursal_id) y el id se repite entre sucursales (en bodega3: 5.468 retiros, 5.370 gastos,
 * 5.110 cajas): el orden termina en id + sucursal. Corre en CI, sin base.
 */
class OrdenTotalCajaTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "RetiroRepository              | findByAllPage                   | order by r.id desc, r.sucursalId desc",
            "RetiroRepository              | findByAll                       | order by r.id desc, r.sucursalId desc",
            "RetiroRepository              | findFlotantes                   | order by r.creadoEn desc, r.id desc, r.sucursalId desc",
            "RetiroRepository              | findFlotantesEnSucursales       | order by r.creadoEn desc, r.id desc, r.sucursalId desc",
            "GastoRepository               | findByAllPage                   | order by r.id desc, r.sucursalId desc",
            "PdvCajaRepository             | findAllWithFilters              | order by c.creadoEn desc, c.id desc, c.sucursalId desc",
            "RetiroCasoRepository          | filter                          | order by c.creadoEn desc, c.id desc",
            "MovimientoBancarioRepository  | filter                          | order by m.creadoEn desc, m.id desc",
            "MovimientoCajaVirtualRepository | filter                        | order by m.creadoEn desc, m.id desc",
            "VentaTarjetaRepository        | filterVentaTarjeta              | ORDER BY vt.creadoEn DESC, vt.id DESC, vt.sucursalId DESC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName("com.franco.dev.repository.financiero." + repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertFalse(consultas.isEmpty(), metodo);
        consultas.forEach(q -> assertTrue(q.endsWith(final_), metodo + ": " + q));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "PdvCajaRepository               | findByUsuarioIdOrderByIdDescSucursalIdDesc",
            "EntradaVariaRepository          | findByCajaVirtualIdOrderByCreadoEnDescIdDesc",
            "MovimientoBancarioRepository    | findByCuentaBancariaIdOrderByCreadoEnDescIdDesc",
            "MovimientoCajaVirtualRepository | findByCajaVirtualIdOrderByCreadoEnDescIdDesc",
            "MovimientoCajaVirtualRepository | findByCajaVirtualIdAndCreadoEnBetweenOrderByCreadoEnDescIdDesc",
            "MovimientoClienteRepository     | findByClienteIdOrderByCreadoEnDescIdDesc",
            "OperacionFinancieraRepository   | findAllByOrderByCreadoEnDescIdDesc",
            "VentaCreditoRepository          | findAllByClienteIdAndEstadoOrderByCreadoEnDescIdDescSucursalIdDesc",
            "VentaCreditoRepository          | findAllByClienteIdOrderByCreadoEnDescIdDescSucursalIdDesc"
    })
    void lasDerivadasPaginadasDesempatan(String repo, String metodo) throws Exception {
        assertTrue(Arrays.stream(Class.forName("com.franco.dev.repository.financiero." + repo).getMethods())
                .anyMatch(m -> m.getName().equals(metodo) && Arrays.asList(m.getParameterTypes()).contains(Pageable.class)),
                metodo);
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class SinOrderBy {

        @Mock CajaVirtualService cajaService;
        @Mock TesoreriaSecurityService seg;
        @InjectMocks CajaVirtualGraphQL cajaGraphQL;

        @Mock MaletinRepository maletinRepository;
        @InjectMocks MaletinService maletinService;

        private final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        @Test
        void cajasVirtualesOrdenanPorId() {
            cajaGraphQL.cajaVirtuales(0, 10);
            cajaGraphQL.cajaVirtualesFilter(null, null, null, null, 0, 10);
            verify(cajaService).findAll(any(), pageable.capture());
            verify(cajaService).filter(any(), isNull(), isNull(), isNull(), isNull(), pageable.capture());
            pageable.getAllValues().forEach(p -> assertEquals(Sort.by("id"), p.getSort()));
        }

        @Test
        void maletinesSinSucursalOrdenanPorId() {
            when(maletinRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
            maletinService.findBySucursalIdOrAll(null);
            verify(maletinRepository).findAll(pageable.capture());
            assertEquals(Sort.by("id"), pageable.getValue().getSort());
        }
    }
}
