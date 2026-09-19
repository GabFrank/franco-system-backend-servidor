package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La serie ante la SET es (timbrado, establecimiento, punto de expedicion); la numeracion sale de
 * `findMaxNumeroByTimbradoDetalleId`, que cuenta por fila. Cuando las dos no coinciden se emiten
 * numeros duplicados, y un numero aprobado no se puede deshacer.
 *
 * El caso concreto: al depósito (sucursal 13), que no tiene `codigo_establecimiento_factura`
 * propio y por eso cae al 001, se le dio un timbrado_detalle con id propio. Si además reusa el
 * punto de expedicion de la central, las dos sucursales emiten la nota 1, la 2, la 3…
 */
class SerieDeNumeracionValidatorTest {

    private static final Long TIMBRADO = 7L;

    private TimbradoDetalleRepository repository;
    private SucursalService sucursalService;
    private SerieDeNumeracionValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(TimbradoDetalleRepository.class);
        sucursalService = mock(SucursalService.class);
        validator = new SerieDeNumeracionValidator(repository, sucursalService);
        // Ninguna sucursal tiene codigo propio: todas declaran el establecimiento 001, que es
        // justamente la situacion del depósito.
        when(sucursalService.findById(anyLong())).thenReturn(Optional.of(new Sucursal()));
    }

    @Test
    void dosFilasActivasConElMismoPuntoSonColision() {
        TimbradoDetalle central = detalle(105L, 1L, "001", true);
        TimbradoDetalle deposito = detalle(117L, 13L, "001", true);
        when(repository.findByTimbradoId(TIMBRADO)).thenReturn(Arrays.asList(central, deposito));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(deposito));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("001-001"), e.getMessage());
    }

    @Test
    void elMismoIdEnDosSucursalesNoEsColision() {
        // Comparten contador a proposito: es el diseño que hace que el depósito siga la serie de
        // la central en vez de arrancar de cero.
        TimbradoDetalle central = detalle(105L, 1L, "001", true);
        TimbradoDetalle deposito = detalle(105L, 13L, "001", true);
        when(repository.findByTimbradoId(TIMBRADO)).thenReturn(Arrays.asList(central, deposito));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(deposito));
    }

    @Test
    void puntosDeExpedicionDistintosNoSonColision() {
        TimbradoDetalle central = detalle(105L, 1L, "001", true);
        TimbradoDetalle deposito = detalle(117L, 13L, "002", true);
        when(repository.findByTimbradoId(TIMBRADO)).thenReturn(Arrays.asList(central, deposito));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(deposito));
    }

    @Test
    void unaFilaInactivaNoCuenta() {
        TimbradoDetalle central = detalle(105L, 1L, "001", false);
        TimbradoDetalle deposito = detalle(117L, 13L, "001", true);
        when(repository.findByTimbradoId(TIMBRADO)).thenReturn(Arrays.asList(central, deposito));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(deposito));
    }

    private static TimbradoDetalle detalle(Long id, Long sucursalId, String punto, boolean activo) {
        Timbrado timbrado = new Timbrado();
        timbrado.setId(TIMBRADO);
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(id);
        detalle.setSucursalId(sucursalId);
        detalle.setPuntoExpedicion(punto);
        detalle.setActivo(activo);
        detalle.setTimbrado(timbrado);
        return detalle;
    }
}
