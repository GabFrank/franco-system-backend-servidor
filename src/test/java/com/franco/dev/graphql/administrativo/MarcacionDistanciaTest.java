package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.MetodoMarcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.administrativo.helper.DistanciaMarcacion;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.RrhhSecurityFalso;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * saveMarcacion: mas de 80 m de la sucursal no se marca. La distancia la mide el servidor.
 */
class MarcacionDistanciaTest {

    /** El deposito Aquario, con la coordenada de produccion. */
    private static final double LAT = -24.06339177105442;
    private static final double LNG = -54.31604944224273;
    /** Un grado de latitud son ~111.195 m con el radio que usa el haversine. */
    private static final double METRO_LAT = 1 / 111_194.93;

    private MarcacionService service;
    private SucursalService sucursalService;
    private MarcacionGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(MarcacionService.class);
        sucursalService = mock(SucursalService.class);
        resolver = new MarcacionGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(resolver, "sucursalService", sucursalService);
        ReflectionTestUtils.setField(resolver, "seg", new RrhhSecurityFalso());
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sucursalService.findById(13L)).thenReturn(Optional.of(sucursal(LAT + ", " + LNG)));
    }

    private static Sucursal sucursal(String localizacion) {
        Sucursal s = new Sucursal();
        s.setId(13L);
        s.setNombre("DEPOSITO AQUARIO SDG");
        s.setLocalizacion(localizacion);
        return s;
    }

    /** Una marcacion de la PWA a {@code metros} al norte de la sucursal. */
    private static MarcacionInput aMetros(double metros) {
        MarcacionInput in = new MarcacionInput();
        in.setSucursalId(13L);
        in.setUsuarioId(40L);
        in.setTipo(TipoMarcacion.ENTRADA);
        in.setMetodoRegistro(MetodoMarcacion.FACIAL_1A1);
        in.setLatitud(BigDecimal.valueOf(LAT + metros * METRO_LAT));
        in.setLongitud(BigDecimal.valueOf(LNG));
        in.setDistanciaSucursalMetros(0);
        return in;
    }

    private Marcacion guardada() {
        ArgumentCaptor<Marcacion> c = ArgumentCaptor.forClass(Marcacion.class);
        verify(service).save(c.capture());
        return c.getValue();
    }

    @Test
    void adentroSeMarcaYSeGuardaLaDistanciaDelServidor() {
        resolver.saveMarcacion(aMetros(30));
        // El cliente mando 0: se guarda la que midio el servidor.
        assertEquals(30, guardada().getDistanciaSucursalMetros());
    }

    @Test
    void justoEnElLimiteSeMarca() {
        resolver.saveMarcacion(aMetros(80));
        assertEquals(80, guardada().getDistanciaSucursalMetros());
    }

    @Test
    void masAllaDelLimiteSeRechazaSinGuardar() {
        GraphQLException ex = assertThrows(GraphQLException.class, () -> resolver.saveMarcacion(aMetros(81)));
        assertTrue(ex.getMessage().contains("81 m"), ex.getMessage());
        assertTrue(ex.getMessage().contains("80 m o menos"), ex.getMessage());
        verifyNoInteractions(service);
    }

    @Test
    void elRolDeRrhhNoHabilitaAMarcarLejos() {
        ReflectionTestUtils.setField(resolver, "seg", new RrhhSecurityFalso("RRHH GESTIONAR"));
        assertThrows(GraphQLException.class, () -> resolver.saveMarcacion(aMetros(400)));
        verifyNoInteractions(service);
    }

    @Test
    void laPwaSinUbicacionSeRechaza() {
        MarcacionInput in = aMetros(0);
        in.setLatitud(null);
        in.setLongitud(null);
        assertThrows(GraphQLException.class, () -> resolver.saveMarcacion(in));
        verifyNoInteractions(service);
    }

    @Test
    void elCeroCeroCuentaComoSinUbicacion() {
        MarcacionInput in = aMetros(0);
        in.setLatitud(BigDecimal.ZERO);
        in.setLongitud(BigDecimal.ZERO);
        assertThrows(GraphQLException.class, () -> resolver.saveMarcacion(in));
        verifyNoInteractions(service);
    }

    @Test
    void elDesktopSinGpsNiMetodoSigueMarcando() {
        MarcacionInput in = new MarcacionInput();
        in.setSucursalId(13L);
        in.setUsuarioId(40L);
        in.setTipo(TipoMarcacion.ENTRADA);
        resolver.saveMarcacion(in);
        assertNull(guardada().getDistanciaSucursalMetros());
    }

    @Test
    void laSucursalSinCoordenadasNoDejaMarcarConGps() {
        when(sucursalService.findById(13L)).thenReturn(Optional.of(sucursal("  ")));
        assertThrows(GraphQLException.class, () -> resolver.saveMarcacion(aMetros(10)));
        verifyNoInteractions(service);
    }

    @Test
    void editarUnaMarcacionExistenteNoPasaPorLaDistancia() {
        ReflectionTestUtils.setField(resolver, "seg", new RrhhSecurityFalso("RRHH GESTIONAR"));
        MarcacionInput in = aMetros(400);
        in.setId(99L);
        resolver.saveMarcacion(in);
        verify(service).save(any());
    }

    @Test
    void coordenadasMalCargadasNoSeLeen() {
        assertNull(DistanciaMarcacion.coordenadas(null));
        assertNull(DistanciaMarcacion.coordenadas(""));
        assertNull(DistanciaMarcacion.coordenadas("-24.5"));
        assertNull(DistanciaMarcacion.coordenadas(", -54.3"));
        assertNull(DistanciaMarcacion.coordenadas("abc, def"));
        assertArrayEquals(new double[]{-24.5, -54.3}, DistanciaMarcacion.coordenadas(" -24.5 , -54.3 "));
    }
}
