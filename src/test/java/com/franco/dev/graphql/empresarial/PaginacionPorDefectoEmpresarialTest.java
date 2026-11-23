package com.franco.dev.graphql.empresarial;

import com.franco.dev.repository.empresarial.SucursalRepository;
import com.franco.dev.service.empresarial.ImpresoraService;
import com.franco.dev.service.empresarial.PuntoDeVentaService;
import com.franco.dev.service.empresarial.SucursalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #582, tanda empresarial. findByNombre recibe Integer sin default: sin page/size daba
 * NullPointerException. impresoras y puntoDeVentas reciben int: un page faltante llega como 0
 * (graphql-java-kickstart), pero un size null explicito llegaba como 0 y PageRequest.of fallaba.
 */
class PaginacionPorDefectoEmpresarialTest {

    @Mock private SucursalService sucursalService;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private ImpresoraService impresoraService;
    @Mock private PuntoDeVentaService puntoDeVentaService;

    @InjectMocks private SucursalGraphQL sucursalGraphQL;
    @InjectMocks private ImpresoraGraphQL impresoraGraphQL;
    @InjectMocks private PuntoDeVentaGraphQL puntoDeVentaGraphQL;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(sucursalService.getRepository()).thenReturn(sucursalRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static void esPrimeraPaginaDe15(Pageable p) {
        assertEquals(0, p.getPageNumber());
        assertEquals(15, p.getPageSize());
    }

    @Test
    void findByNombreSinPageNiSize() {
        sucursalGraphQL.findByNombre("central", null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(sucursalRepository).findByNombreLike(eq("%CENTRAL%"), p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }

    @Test
    void findByNombreSinNombreRespetaPageYSize() {
        sucursalGraphQL.findByNombre(null, 1, 20);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(sucursalRepository).findAll(p.capture());
        assertEquals(1, p.getValue().getPageNumber());
        assertEquals(20, p.getValue().getPageSize());
    }

    @Test
    void puntoDeVentasComoLaPideElDesktop() {
        // add-timbrado-detalle-dialog: puntoDeVentas(size: 50), sin page
        puntoDeVentaGraphQL.puntoDeVentas(null, 50);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(puntoDeVentaService).findAll(p.capture());
        assertEquals(0, p.getValue().getPageNumber());
        assertEquals(50, p.getValue().getPageSize());
    }

    @Test
    void impresorasConSizeNulo() {
        impresoraGraphQL.impresoras(null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(impresoraService).findAll(p.capture());
        esPrimeraPaginaDe15(p.getValue());
    }
}
