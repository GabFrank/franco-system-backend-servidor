package com.franco.dev.graphql.configuracion;

import com.franco.dev.repository.configuracion.InicioSesionGraphQL;
import com.franco.dev.service.configuracion.InicioSesionService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * #582, tanda configuracion: inicioSesionListPorUsuarioIdAndAbierto hacia PageRequest.of con Integer
 * nulos (NullPointerException sin page/size).
 */
class PaginacionPorDefectoConfiguracionTest {

    @Mock private InicioSesionService service;
    @InjectMocks private InicioSesionGraphQL resolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void sesionesAbiertasSinPageNiSize() {
        resolver.inicioSesionListPorUsuarioIdAndAbierto(494L, null, null, null);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findByUsuarioIdAndHoraFinIsNul(eq(494L), isNull(), p.capture());
        assertEquals(0, p.getValue().getPageNumber());
        assertEquals(15, p.getValue().getPageSize());
    }

    @Test
    void sesionesAbiertasComoLaPideLaPwa() {
        resolver.inicioSesionListPorUsuarioIdAndAbierto(494L, null, 0, 50);

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findByUsuarioIdAndHoraFinIsNul(eq(494L), isNull(), p.capture());
        assertEquals(0, p.getValue().getPageNumber());
        assertEquals(50, p.getValue().getPageSize());
    }
}
