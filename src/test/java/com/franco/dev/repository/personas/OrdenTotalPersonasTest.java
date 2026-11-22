package com.franco.dev.repository.personas;

import com.franco.dev.graphql.personas.ProveedorGraphQL;
import com.franco.dev.graphql.personas.ProveedorServicioGraphQL;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.personas.ProveedorServicioService;
import com.franco.dev.service.personas.ProveedorService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tanda de personas y RRHH de #674: cada consulta paginada termina su orden en una clave unica
 * (en marcaciones, jornadas y sesiones la PK es id + sucursal). Corre en CI, sin base.
 */
class OrdenTotalPersonasTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "personas.ClienteRepository             | findByAll                       | order by per.nombre asc, c.id asc",
            "personas.FuncionarioRepository         | searchPage                      | order by p.nombre, f.id",
            "personas.FuncionarioRepository         | findAllWithFilterAndPage        | order by p.nombre, u.id",
            "rrhh.RrhhAlcanceRepository             | buscarFuncionarios              | order by f.persona.nombre, f.id",
            "productos.ProductoProveedorRepository  | findByProveedorIdOrderByProductoDescripcionAsc | ORDER BY pp.producto.descripcion ASC, pp.id ASC",
            "productos.ProductoProveedorRepository  | findByProveedorIdAndProductoDescripcionLikeIgnoreCase | order by prod.descripcion asc, pp.id asc",
            "productos.ProductoProveedorRepository  | findByProductoIdAndActivoTrueOrderByProveedorPersonaNombre | ORDER BY per.nombre ASC, pp.id ASC",
            "administrativo.JornadaRepository       | findHistorial                   | j.id DESC, j.sucursalId DESC"
    })
    void elOrdenTerminaEnUnaClaveUnica(String repo, String metodo, String final_) throws Exception {
        List<String> consultas = Arrays.stream(Class.forName("com.franco.dev.repository." + repo).getMethods())
                .filter(m -> m.getName().equals(metodo) && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value().trim())
                .collect(Collectors.toList());
        assertFalse(consultas.isEmpty(), metodo);
        consultas.forEach(q -> assertTrue(q.endsWith(final_), metodo + ": " + q));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "configuracion.InicioSesionRepository | findByUsuarioIdAndHoraFinIsNullOrderByIdDescSucursalIdDesc",
            "configuracion.InicioSesionRepository | findByUsuarioIdAndSucursalIdAndHoraFinIsNullOrderByIdDescSucursalIdDesc",
            "administrativo.JornadaRepository     | findByUsuarioIdOrderByFechaDescIdDescSucursalIdDesc",
            "rrhh.LiquidacionSueldoRepository     | findByFuncionarioIdAndEstadoOrderByPeriodoDescIdDesc"
    })
    void lasDerivadasPaginadasDesempatan(String repo, String metodo) throws Exception {
        assertTrue(Arrays.stream(Class.forName("com.franco.dev.repository." + repo).getMethods())
                .anyMatch(m -> m.getName().equals(metodo)), metodo);
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Resolvers {

        @Mock MarcacionRepository marcacionRepository;
        @InjectMocks MarcacionService marcacionService;

        @Mock ProveedorService proveedorService;
        @Mock ProveedorRepository proveedorRepository;
        @InjectMocks ProveedorGraphQL proveedorGraphQL;

        @Mock ProveedorServicioService proveedorServicioService;
        @Mock ProveedorServicioRepository proveedorServicioRepository;
        @InjectMocks ProveedorServicioGraphQL proveedorServicioGraphQL;

        private final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        @Test
        void marcacionesOrdenanPorIdYSucursal() {
            marcacionService.findAllPaged(0, 10);
            marcacionService.findByUsuarioId(1L, 0, 10);
            marcacionService.findByFechaRange("2026-01-01", "2026-01-31", 0, 10);
            marcacionService.findByUsuarioIdAndFechaRange(1L, "2026-01-01", "2026-01-31", 0, 10);
            verify(marcacionRepository).findAll(pageable.capture());
            verify(marcacionRepository).findByUsuarioId(anyLong(), pageable.capture());
            verify(marcacionRepository).findByFechaRange(anyString(), anyString(), pageable.capture());
            verify(marcacionRepository).findByUsuarioIdAndFechaRange(anyLong(), anyString(), anyString(), pageable.capture());
            pageable.getAllValues().forEach(p ->
                    assertEquals(Sort.by(Sort.Direction.DESC, "id", "sucursalId"), p.getSort()));
        }

        @Test
        void proveedoresOrdenanPorNombreEId() {
            when(proveedorService.getRepository()).thenReturn(proveedorRepository);
            proveedorGraphQL.proveedorSearchByPersonaPage(null, 0, 10, null);
            verify(proveedorRepository).findAll(pageable.capture());
            assertEquals(Sort.by("persona.nombre", "id"), pageable.getValue().getSort());
        }

        @Test
        void proveedoresDeServicioOrdenanPorNombreEId() {
            when(proveedorServicioService.getRepository()).thenReturn(proveedorServicioRepository);
            proveedorServicioGraphQL.proveedorServicioSearchPage(null, 0, 10);
            verify(proveedorServicioRepository).findAll(pageable.capture());
            assertEquals(Sort.by("persona.nombre", "id"), pageable.getValue().getSort());
        }
    }
}
