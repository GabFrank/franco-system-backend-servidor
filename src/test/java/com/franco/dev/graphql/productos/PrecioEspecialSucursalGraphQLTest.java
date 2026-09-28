package com.franco.dev.graphql.productos;

import com.franco.dev.service.productos.PrecioEspecialSucursalService;
import com.franco.dev.service.productos.PrecioSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

/**
 * Tope de pagina de filterPreciosEspeciales: la query es abierta a cualquier sesion, asi que
 * page/size se normalizan antes de llegar al service (ver SPEC-PRECIO-ESPECIAL-SUCURSAL.md).
 */
class PrecioEspecialSucursalGraphQLTest {

    @Mock private PrecioSecurityService seg;
    @Mock private PrecioEspecialSucursalService service;
    private PrecioEspecialSucursalGraphQL resolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resolver = new PrecioEspecialSucursalGraphQL(service, seg);
    }

    @AfterEach
    void tearDown() throws Exception { mocks.close(); }

    @Test
    void pageNegativoYSizeGigantePasanAcotados() {
        resolver.filterPreciosEspeciales(null, null, null, -1, 1000);
        verify(service).filtrar(null, null, null, 0, 100);
    }

    @Test
    void sizeCeroPasaComoUno() {
        resolver.filterPreciosEspeciales(null, null, null, 0, 0);
        verify(service).filtrar(null, null, null, 0, 1);
    }

    @Test
    void sinPageNiSizePasaLosDefaults() {
        resolver.filterPreciosEspeciales(null, null, null, null, null);
        verify(service).filtrar(null, null, null, 0, 15);
    }
}
