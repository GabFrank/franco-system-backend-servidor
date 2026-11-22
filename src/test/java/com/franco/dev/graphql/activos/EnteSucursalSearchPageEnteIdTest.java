package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.service.activos.EnteConsultaService;
import com.franco.dev.service.activos.EnteSucursalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #774: «Vehículo por sucursal» filtra por el bien elegido (enteId), no por su descripción como texto.
 */
@ExtendWith(MockitoExtension.class)
class EnteSucursalSearchPageEnteIdTest {

    @Mock
    private EnteSucursalService enteSucursalService;

    @Mock
    private EnteConsultaService consultaService;

    @InjectMocks
    private EnteGraphQL graphQL;

    private final Page<EnteSucursal> vacia = new PageImpl<>(Collections.emptyList());

    @Test
    @DisplayName("el enteId elegido llega a la consulta")
    void pasaEnteId() {
        when(enteSucursalService.findAllWithFilters(null, null, TipoEnte.VEHICULO, null, 3L, 0, 15)).thenReturn(vacia);

        graphQL.enteSucursalSearchPage(null, null, TipoEnte.VEHICULO, null, 0, 15, 3L);

        verify(enteSucursalService).findAllWithFilters(isNull(), isNull(), eq(TipoEnte.VEHICULO), isNull(), eq(3L), eq(0), eq(15));
    }

    @Test
    @DisplayName("sin enteId la consulta no filtra por bien (llamadores viejos)")
    void sinEnteId() {
        when(enteSucursalService.findAllWithFilters("x", 1L, TipoEnte.VEHICULO, 2L, null, 0, 15)).thenReturn(vacia);

        graphQL.enteSucursalSearchPage("x", 1L, TipoEnte.VEHICULO, 2L, null, null, null);

        verify(enteSucursalService).findAllWithFilters(eq("x"), eq(1L), eq(TipoEnte.VEHICULO), eq(2L), isNull(), eq(0), eq(15));
    }
}
