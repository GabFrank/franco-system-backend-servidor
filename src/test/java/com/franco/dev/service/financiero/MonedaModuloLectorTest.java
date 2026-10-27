package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.repository.financiero.ConfiguracionMonedaModuloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fija como se resuelve la moneda de un modulo (V258.5): sin filas, la de la empresa (lo de hoy);
 * la fila activa mas reciente ya vigente; desactivar vuelve a la anterior; sucursal solo en PDV; y
 * que una falla al leer nunca llega a quien llama.
 */
class MonedaModuloLectorTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 2, 10, 0);

    private ConfiguracionMonedaModuloRepository repository;
    private MonedaService monedaService;
    private MonedaModuloLector lector;
    private Moneda guarani;
    private Moneda dolar;
    private Moneda real;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionMonedaModuloRepository.class);
        monedaService = mock(MonedaService.class);
        lector = new MonedaModuloLector(repository, monedaService);
        guarani = moneda(1L, "GUARANI");
        dolar = moneda(2L, "DOLAR");
        real = moneda(3L, "REAL");
        when(monedaService.empresa()).thenReturn(guarani);
        when(repository.findByModuloOrderByVigenteDesdeDescIdDesc(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void sinFilas_esLaMonedaDeLaEmpresa() {
        for (ModuloMoneda m : ModuloMoneda.values()) {
            assertSame(guarani, lector.resolver(m, 7L, AHORA), m.name());
        }
    }

    @Test
    void laFilaMasRecienteYaVigenteGana() {
        filas(ModuloMoneda.COMPRAS,
                fila(11L, dolar, null, AHORA.minusDays(1), true),
                fila(10L, real, null, AHORA.minusDays(10), true));
        assertSame(dolar, lector.resolver(ModuloMoneda.COMPRAS, null, AHORA));
    }

    @Test
    void unaFilaConVigenciaFuturaTodaviaNoRige() {
        filas(ModuloMoneda.COMPRAS,
                fila(11L, dolar, null, AHORA.plusDays(1), true),
                fila(10L, real, null, AHORA.minusDays(10), true));
        assertSame(real, lector.resolver(ModuloMoneda.COMPRAS, null, AHORA));
        assertSame(dolar, lector.resolver(ModuloMoneda.COMPRAS, null, AHORA.plusDays(2)));
    }

    @Test
    void desactivarLaUltimaVuelveALaAnterior_yDesactivarTodasVuelveALaEmpresa() {
        filas(ModuloMoneda.COSTOS,
                fila(11L, dolar, null, AHORA.minusDays(1), false),
                fila(10L, real, null, AHORA.minusDays(10), true));
        assertSame(real, lector.resolver(ModuloMoneda.COSTOS, null, AHORA));

        filas(ModuloMoneda.COSTOS,
                fila(11L, dolar, null, AHORA.minusDays(1), false),
                fila(10L, real, null, AHORA.minusDays(10), false));
        assertSame(guarani, lector.resolver(ModuloMoneda.COSTOS, null, AHORA));
    }

    @Test
    void activoNullCuentaComoActiva() {
        filas(ModuloMoneda.PRECIOS, fila(10L, dolar, null, AHORA.minusDays(1), null));
        assertSame(dolar, lector.resolver(ModuloMoneda.PRECIOS, null, AHORA));
    }

    @Test
    void pdv_laSucursalPisaALaGlobal_yOtraSucursalSigueALaGlobal() {
        filas(ModuloMoneda.PDV,
                fila(12L, real, 7L, AHORA.minusDays(1), true),
                fila(11L, dolar, null, AHORA.minusDays(2), true));
        assertSame(real, lector.resolver(ModuloMoneda.PDV, 7L, AHORA));
        assertSame(dolar, lector.resolver(ModuloMoneda.PDV, 8L, AHORA));
        assertSame(dolar, lector.resolver(ModuloMoneda.PDV, null, AHORA));
    }

    @Test
    void fueraDelPdv_laSucursalNoSeConsidera() {
        // El CHECK de V258.5 lo impide; si una fila asi llegara igual, no rige.
        filas(ModuloMoneda.COMPRAS, fila(12L, real, 7L, AHORA.minusDays(1), true));
        assertSame(guarani, lector.resolver(ModuloMoneda.COMPRAS, 7L, AHORA));
    }

    @Test
    void unaFallaAlLeer_caeALaEmpresaSinLanzar() {
        when(repository.findByModuloOrderByVigenteDesdeDescIdDesc(any())).thenThrow(new RuntimeException("db caida"));
        assertSame(guarani, lector.resolver(ModuloMoneda.COMPRAS, null, AHORA));
    }

    @Test
    void siTampocoSePuedeLeerLaEmpresa_devuelveNullSinLanzar() {
        when(repository.findByModuloOrderByVigenteDesdeDescIdDesc(any())).thenThrow(new RuntimeException("db caida"));
        when(monedaService.empresa()).thenThrow(new RuntimeException("db caida"));
        assertNull(lector.resolver(ModuloMoneda.COMPRAS, null, AHORA));
    }

    private void filas(ModuloMoneda modulo, ConfiguracionMonedaModulo... filas) {
        when(repository.findByModuloOrderByVigenteDesdeDescIdDesc(modulo)).thenReturn(Arrays.asList(filas));
    }

    private static ConfiguracionMonedaModulo fila(Long id, Moneda moneda, Long sucursalId, LocalDateTime desde, Boolean activo) {
        ConfiguracionMonedaModulo f = new ConfiguracionMonedaModulo();
        f.setId(id);
        f.setMoneda(moneda);
        if (sucursalId != null) {
            Sucursal s = new Sucursal();
            s.setId(sucursalId);
            f.setSucursal(s);
        }
        f.setVigenteDesde(desde);
        f.setActivo(activo);
        return f;
    }

    static Moneda moneda(Long id, String denominacion) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        return m;
    }
}
