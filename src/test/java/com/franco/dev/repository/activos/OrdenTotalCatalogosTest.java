package com.franco.dev.repository.activos;

import com.franco.dev.graphql.empresarial.SucursalGraphQL;
import com.franco.dev.graphql.operaciones.HojaRutaGraphQL;
import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.HojaRutaService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tanda de catalogos y activos de #674: las busquedas paginadas que no tenian ORDER BY ahora
 * ordenan por id, y las que ordenaban por una columna repetida desempatan por id. Corre en CI.
 */
class OrdenTotalCatalogosTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "financiero.EnteFinancieroRepository | findByAllWithPage                   | order by ef.id desc",
            "activos.FamiliaMuebleRepository     | findByAllWithPage                   | order by f.id desc",
            "activos.ModeloRepository            | findByAllWithPage                   | order by m.id desc",
            "activos.ModeloRepository            | findByMarcaIdAndTexto               | order by m.id desc",
            "activos.TipoMuebleRepository        | findByAllWithPage                   | order by t.id desc",
            "activos.TipoVehiculoRepository      | findByAllWithPage                   | order by t.id desc",
            "equipos.ModeloEquipoRepository      | findByMarcaIdAndTexto               | order by m.id desc",
            "activos.TelemetriaRepository        | findByDispositivoIdAndFechaGpsBetween | ORDER BY t.fechaGps DESC, t.id DESC",
            "financiero.EnteCuotaRepository      | findAllByEnteFinancieroId           | order by ec.numeroCuota asc, ec.id asc"
    })
    void elOrdenTerminaEnElId(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName("com.franco.dev.repository." + repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertEquals(1, consultas.size(), metodo);
        assertTrue(consultas.get(0).endsWith(final_), metodo + ": " + consultas.get(0));
    }

    /** Los resolvers que llaman a una derivada o a findAll sin orden pasan un Sort por id. */
    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Resolvers {

        @Mock SucursalService sucursalService;
        @InjectMocks SucursalGraphQL sucursalGraphQL;

        @Mock HojaRutaService hojaRutaService;
        @InjectMocks HojaRutaGraphQL hojaRutaGraphQL;

        private final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        @Test
        void sucursalesPorNombreOrdenanPorId() {
            SucursalRepository repo = mock(SucursalRepository.class);
            when(sucursalService.getRepository()).thenReturn(repo);
            sucursalGraphQL.findByNombre(null, 0, 10);
            sucursalGraphQL.findByNombre("central", 0, 10);
            verify(repo).findAll(pageable.capture());
            verify(repo).findByNombreLike(anyString(), pageable.capture());
            pageable.getAllValues().forEach(p -> assertEquals(Sort.by("id"), p.getSort()));
        }

        @Test
        @SuppressWarnings("unchecked")
        void hojasDeRutaPorVehiculoYChoferOrdenanPorIdDesc() {
            when(hojaRutaService.findByVehiculoId(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());
            when(hojaRutaService.findByChoferId(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());
            hojaRutaGraphQL.hojaRutaPorVehiculo(1L, 0, 10);
            hojaRutaGraphQL.hojaRutaPorChofer(1L, 0, 10);
            verify(hojaRutaService).findByVehiculoId(anyLong(), pageable.capture());
            verify(hojaRutaService).findByChoferId(anyLong(), pageable.capture());
            pageable.getAllValues().forEach(p -> assertEquals(Sort.by(Sort.Direction.DESC, "id"), p.getSort()));
        }
    }
}
