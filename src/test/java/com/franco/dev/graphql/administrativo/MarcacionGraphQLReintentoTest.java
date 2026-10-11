package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;

import javax.persistence.RollbackException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MarcacionService.save corre en SERIALIZABLE: dos marcaciones casi simultaneas hacen que
 * Postgres cancele una con SQLState 40001 y pida reintentar. Nadie lo hacia, y a quien marcaba
 * le salia "could not execute statement" (bodega, 06/10/2026 08:00:48, una PWA en la sucursal 13
 * contra un desktop en la 0).
 *
 * El choque real no se arma con mocks: aca se prueba que reintenta lo que corresponde, que no
 * reintenta lo demas, y —lo que mas importa— que cada intento arma una entidad nueva.
 */
class MarcacionGraphQLReintentoTest {

    private MarcacionService service;
    private MarcacionGraphQL resolver;
    /** El id con el que cada entidad llego a save(), antes de que el servicio la toque. */
    private List<Long> idsAlEntrar;
    private List<Marcacion> recibidas;

    @BeforeEach
    void setUp() {
        service = mock(MarcacionService.class);
        resolver = new MarcacionGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(resolver, "sucursalService", mock(SucursalService.class));
        idsAlEntrar = new ArrayList<>();
        recibidas = new ArrayList<>();
    }

    @AfterEach
    void limpiarInterrupcion() {
        Thread.interrupted();
    }

    private static MarcacionInput entrada() {
        MarcacionInput input = new MarcacionInput();
        input.setSucursalId(13L);
        input.setTipo(TipoMarcacion.ENTRADA);
        return input;
    }

    /** Como llega el 40001 cuando salta en una sentencia: lo que quedo en el log de bodega. */
    private static RuntimeException choqueEnSentencia() {
        return new CannotAcquireLockException("could not execute statement",
                new SQLException("could not serialize access due to read/write dependencies among transactions",
                        "40001"));
    }

    /**
     * save() se comporta como el servicio real en lo que importa: le pone id a la entidad que
     * recibe, y recien despues falla o devuelve.
     */
    private void alGuardar(RuntimeException... fallos) {
        final int[] llamada = {0};
        when(service.save(any(Marcacion.class))).thenAnswer(inv -> {
            Marcacion m = inv.getArgument(0);
            recibidas.add(m);
            idsAlEntrar.add(m.getId());
            int n = llamada[0]++;
            if (m.getId() == null) {
                m.setId(4001L + 2L * n);
            }
            if (n < fallos.length) {
                throw fallos[n];
            }
            return m;
        });
    }

    @Test
    void siChocaUnaVezReintentaYGuardaConUnaEntidadNueva() {
        alGuardar(choqueEnSentencia());

        Marcacion guardada = resolver.saveMarcacion(entrada());

        verify(service, times(2)).save(any(Marcacion.class));
        assertNotSame(recibidas.get(0), recibidas.get(1),
                "reusar la entidad mandaria el id del intento fallido, que otra transaccion pudo ocupar");
        assertNull(idsAlEntrar.get(0));
        assertNull(idsAlEntrar.get(1), "el segundo intento tiene que pedir un id nuevo");
        assertSame(recibidas.get(1), guardada);
    }

    @Test
    void reconoceElChoqueEnvueltoComoLoDejaProcesarJornada() {
        alGuardar(new RuntimeException("Error procesando jornada de marcación", choqueEnSentencia()));

        resolver.saveMarcacion(entrada());

        verify(service, times(2)).save(any(Marcacion.class));
    }

    @Test
    void reconoceElChoqueQueSaltaEnElCommit() {
        RollbackException rollback = new RollbackException("Error while committing the transaction",
                new SQLException("could not serialize access due to read/write dependencies", "40001"));
        alGuardar(new TransactionSystemException("Could not commit JPA transaction", rollback));

        resolver.saveMarcacion(entrada());

        verify(service, times(2)).save(any(Marcacion.class));
    }

    @Test
    void unDeadlockTambienSeReintenta() {
        alGuardar(new CannotAcquireLockException("deadlock", new SQLException("deadlock detected", "40P01")));

        resolver.saveMarcacion(entrada());

        verify(service, times(2)).save(any(Marcacion.class));
    }

    @Test
    void siChocaSiempreSeRindeConUnMensajeQueSePuedeLeer() {
        RuntimeException[] choques = new RuntimeException[MarcacionGraphQL.INTENTOS_GUARDADO];
        for (int i = 0; i < choques.length; i++) {
            choques[i] = choqueEnSentencia();
        }
        RuntimeException ultimo = choques[choques.length - 1];
        alGuardar(choques);

        RuntimeException error = assertThrows(RuntimeException.class, () -> resolver.saveMarcacion(entrada()));

        verify(service, times(MarcacionGraphQL.INTENTOS_GUARDADO)).save(any(Marcacion.class));
        // GraphqlExceptionHandler solo desanida los GraphQLError: sin eso llega con el prefijo
        // "Exception while fetching data".
        assertTrue(error instanceof GraphQLError);
        assertEquals(MarcacionGraphQL.MENSAJE_CHOQUE, error.getMessage());
        assertSame(ultimo, error.getCause());
    }

    @Test
    void unaValidacionSaleAlPrimerIntentoConSuMensaje() {
        alGuardar(new IllegalStateException("Ya registró entrada en la jornada del día."));

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> resolver.saveMarcacion(entrada()));

        assertEquals("Ya registró entrada en la jornada del día.", error.getMessage());
        verify(service, times(1)).save(any(Marcacion.class));
    }

    @Test
    void unErrorCualquieraNoSeReintenta() {
        alGuardar(new NullPointerException("otro problema"));

        assertThrows(NullPointerException.class, () -> resolver.saveMarcacion(entrada()));

        verify(service, times(1)).save(any(Marcacion.class));
    }

    @Test
    void unaClaveDuplicadaNoSeReintenta() {
        alGuardar(new DataIntegrityViolationException("duplicate key",
                new SQLException("duplicate key value violates unique constraint", "23505")));

        assertThrows(DataIntegrityViolationException.class, () -> resolver.saveMarcacion(entrada()));

        verify(service, times(1)).save(any(Marcacion.class));
    }

    @Test
    void unLockTimeoutNoSeReintentaAunqueSeaLaMismaClaseDeSpring() {
        alGuardar(new CannotAcquireLockException("lock timeout",
                new SQLException("canceling statement due to lock timeout", "55P03")));

        assertThrows(CannotAcquireLockException.class, () -> resolver.saveMarcacion(entrada()));

        verify(service, times(1)).save(any(Marcacion.class));
    }

    @Test
    void conIdEnElInputCadaIntentoVuelveABuscarLaExistente() {
        // Cada busqueda devuelve un objeto distinto, como despues de un rollback que vacia la
        // sesion: asi se ve si el segundo intento usa lo que volvio a buscar o lo que le quedo.
        when(service.findById(any(EmbebedPrimaryKey.class))).thenAnswer(inv -> {
            Marcacion existente = new Marcacion();
            existente.setId(77L);
            existente.setSucursalId(13L);
            return Optional.of(existente);
        });
        alGuardar(choqueEnSentencia());
        MarcacionInput input = entrada();
        input.setId(77L);

        resolver.saveMarcacion(input);

        verify(service, times(2)).findById(any(EmbebedPrimaryKey.class));
        verify(service, times(2)).save(any(Marcacion.class));
        assertEquals(Long.valueOf(77L), idsAlEntrar.get(1));
        assertNotSame(recibidas.get(0), recibidas.get(1));
    }

    @Test
    void siElHiloSeInterrumpeEnLaEsperaNoHayOtroIntento() {
        alGuardar(choqueEnSentencia(), choqueEnSentencia());
        Thread.currentThread().interrupt();

        RuntimeException error = assertThrows(RuntimeException.class, () -> resolver.saveMarcacion(entrada()));

        assertEquals(MarcacionGraphQL.MENSAJE_CHOQUE, error.getMessage());
        verify(service, times(1)).save(any(Marcacion.class));
        assertTrue(Thread.currentThread().isInterrupted(), "la interrupcion no se puede tragar");
    }
}
