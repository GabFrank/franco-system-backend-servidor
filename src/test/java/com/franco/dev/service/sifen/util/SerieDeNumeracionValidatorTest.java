package com.franco.dev.service.sifen.util;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
        // Por defecto ninguna sucursal tiene codigo propio: todas declaran el establecimiento 001,
        // que es justamente la situacion del depósito.
        when(sucursalService.findById(anyLong())).thenReturn(Optional.of(new Sucursal()));
    }

    @Test
    void dosFilasActivasConElMismoPuntoSonColision() {
        filas(fila(105, 1, "001", true), fila(117, 13, "001", true));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(detalle(117L, "001", true), 13L));
        assertTrue(e.getMessage().contains("001-001"), e.getMessage());
    }

    @Test
    void elMismoIdEnDosSucursalesNoEsColision() {
        // Mismo id = mismo contador: para la numeracion no hay duplicado. Esto NO lo vuelve una
        // forma valida de compartir serie: en las filiales la PK es solo (id) y repetir un id traba
        // la replicacion (incidente 2026-09-19). El test solo fija que el validador no lo confunde
        // con una colision.
        filas(fila(105, 1, "001", true), fila(105, 13, "001", true));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(detalle(105L, "001", true), 13L));
    }

    @Test
    void conLaFilaDelDepositoEnSuPropioIdTambienSeFrenaLaCentral() {
        // El estado de produccion despues del incidente: el depósito en la 118 con el mismo punto
        // que la 105 de la central. Las dos filas numeran por separado sobre 001-001, así que se
        // frenan las DOS sucursales, no solo el depósito.
        filas(fila(105, 1, "001", true), fila(118, 13, "001", true));

        assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(detalle(118L, "001", true), 13L));
        assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(detalle(105L, "001", true), 1L));
    }

    @Test
    void puntosDeExpedicionDistintosNoSonColision() {
        filas(fila(105, 1, "001", true), fila(117, 13, "002", true));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(detalle(117L, "002", true), 13L));
    }

    @Test
    void unaFilaInactivaNoCuenta() {
        filas(fila(105, 1, "001", false), fila(117, 13, "001", true));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(detalle(117L, "001", true), 13L));
    }

    @Test
    void elTimbradoConElQueSeEmiteCuentaAunqueEsteInactivo() {
        // La nota de credito emite con el timbrado de la factura, que puede estar inactivo o con
        // activo en NULL. Si otra fila activa tiene su serie, los dos contadores se pisan igual.
        filas(fila(105, 1, "001", true), fila(117, 13, "001", null));

        assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(detalle(117L, "001", null), 13L));
    }

    @Test
    void elPuntoSeComparaComoEnElXml() {
        // El builder emite String.format("%03d", parseInt(punto)): "2" y "002" son la misma serie.
        filas(fila(105, 1, "002", true), fila(117, 13, "2", true));

        assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(detalle(117L, "2", true), 13L));
    }

    @Test
    void elEstablecimientoSaleDeLaSucursalQueEmite() {
        // La sucursal 20 tiene establecimiento propio (005): su fila no choca con la 001 de la
        // central aunque compartan punto. Lo que cuenta es la sucursal de la nota, no la de la fila.
        Sucursal conCodigo = new Sucursal();
        conCodigo.setCodigoEstablecimientoFactura("005");
        when(sucursalService.findById(20L)).thenReturn(Optional.of(conCodigo));
        filas(fila(105, 1, "001", true), fila(130, 20, "001", true));

        assertDoesNotThrow(() -> validator.exigirSerieSinColision(detalle(130L, "001", true), 20L));
        assertThrows(GraphQLException.class,
                () -> validator.exigirSerieSinColision(detalle(130L, "001", true), 13L));
    }

    @Test
    void normalizaElPuntoComoLosBuilders() {
        assertEquals("002", SerieDeNumeracionValidator.normalizarPunto(" 2 "));
        assertEquals("002", SerieDeNumeracionValidator.normalizarPunto("002"));
        assertEquals("", SerieDeNumeracionValidator.normalizarPunto(null));
    }

    private void filas(Object[]... filas) {
        List<Object[]> lista = Arrays.asList(filas);
        when(repository.findFilasDeSerieByTimbradoId(TIMBRADO)).thenReturn(lista);
    }

    /** Como las devuelve la consulta nativa: los bigint llegan como BigInteger. */
    private static Object[] fila(long id, long sucursalId, String punto, Boolean activo) {
        return new Object[]{BigInteger.valueOf(id), BigInteger.valueOf(sucursalId), punto, activo};
    }

    private static TimbradoDetalle detalle(Long id, String punto, Boolean activo) {
        Timbrado timbrado = new Timbrado();
        timbrado.setId(TIMBRADO);
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setId(id);
        detalle.setPuntoExpedicion(punto);
        detalle.setActivo(activo);
        detalle.setTimbrado(timbrado);
        return detalle;
    }
}
