package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que el historial de marcaciones muestra por jornada se calcula en el central, no en el desktop. */
class JornadaServiceCalculosHistorialTest {

    private final JornadaService service = new JornadaService(null);

    private static Marcacion marcacion(String lat, String lng, Integer distancia) {
        Marcacion m = new Marcacion();
        m.setLatitud(lat != null ? new BigDecimal(lat) : null);
        m.setLongitud(lng != null ? new BigDecimal(lng) : null);
        m.setDistanciaSucursalMetros(distancia);
        return m;
    }

    @Test
    void laDistanciaEsLaMayorDeLasMarcacionesQueLaInformaron() {
        Jornada j = new Jornada();
        j.setMarcacionEntrada(marcacion("-24.06", "-54.31", 12));
        j.setMarcacionSalidaAlmuerzo(marcacion(null, null, null));
        j.setMarcacionSalida(marcacion("-24.07", "-54.32", 340));
        assertEquals(340, service.distanciaMaximaMetros(j));
    }

    @Test
    void sinDistanciasNoHayDistanciaMaxima() {
        Jornada j = new Jornada();
        j.setMarcacionEntrada(marcacion(null, null, null));
        assertNull(service.distanciaMaximaMetros(j));
        assertNull(service.distanciaMaximaMetros(new Jornada()));
    }

    @Test
    void tieneUbicacionSiAlgunaMarcacionGuardoCoordenadas() {
        Jornada j = new Jornada();
        j.setMarcacionEntrada(marcacion(null, null, null));
        j.setMarcacionSalida(marcacion("-24.0634397", "-54.3156433", 0));
        assertTrue(service.tieneUbicacion(j));
    }

    @Test
    void ceroCeroYCoordenadasIncompletasNoSonUbicacion() {
        Jornada j = new Jornada();
        j.setMarcacionEntrada(marcacion("0", "0.0000000", 0));
        j.setMarcacionSalida(marcacion("-24.06", null, null));
        assertFalse(service.tieneUbicacion(j));
        assertFalse(service.tieneUbicacion(new Jornada()));
    }

    @Test
    void elAtrasoTotalSumaEntradaYRetornoDelAlmuerzo() {
        Jornada j = new Jornada();
        j.setMinutosLlegadaTardia(6L);
        j.setMinutosLlegadaTardiaAlmuerzo(230L);
        assertEquals(236, service.minutosLlegadaTardiaTotal(j));
        j.setMinutosLlegadaTardia(null);
        j.setMinutosLlegadaTardiaAlmuerzo(null);
        assertEquals(0, service.minutosLlegadaTardiaTotal(j));
    }

    @Test
    void seAjustaA8HorasLaIncompletaYLaQueCerroConExtras() {
        Jornada incompleta = new Jornada();
        incompleta.setEstado(EstadoJornada.INCOMPLETO);
        assertTrue(service.puedeAjustarA8Horas(incompleta));

        Jornada conExtras = new Jornada();
        conExtras.setEstado(EstadoJornada.NORMAL);
        conExtras.setMinutosExtras(95L);
        assertTrue(service.puedeAjustarA8Horas(conExtras));

        Jornada normal = new Jornada();
        normal.setEstado(EstadoJornada.NORMAL);
        normal.setMinutosExtras(0L);
        assertFalse(service.puedeAjustarA8Horas(normal));
    }
}
