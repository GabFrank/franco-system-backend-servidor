package com.franco.dev.graphql;

import com.franco.dev.graphql.financiero.CajaCategoriaObservacionGraphQL;
import com.franco.dev.graphql.financiero.CajaMotivoObservacionGraphQL;
import com.franco.dev.graphql.financiero.CajaSubCategoriaObservacionGraphQL;
import com.franco.dev.graphql.financiero.input.CajaCategoriaObservacionInput;
import com.franco.dev.graphql.financiero.input.CajaMotivoObservacionInput;
import com.franco.dev.graphql.financiero.input.CajaSubCategoriaObservacionInput;
import com.franco.dev.graphql.operaciones.CategoriaObservacionGraphQL;
import com.franco.dev.graphql.operaciones.MotivoObservacionGraphQL;
import com.franco.dev.graphql.operaciones.SubcategoriaObservacionGraphQL;
import com.franco.dev.graphql.operaciones.input.CategoriaObservacionInput;
import com.franco.dev.graphql.operaciones.input.MotivoObservacionInput;
import com.franco.dev.graphql.operaciones.input.SubcategoriaObservacionInput;
import com.franco.dev.graphql.personas.ProveedorGraphQL;
import com.franco.dev.graphql.personas.ProveedorServicioGraphQL;
import com.franco.dev.graphql.personas.input.ProveedorInput;
import com.franco.dev.graphql.personas.input.ProveedorServicioInput;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.RollbackException;
import java.lang.reflect.Field;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #434: los save* con constraint único se tragaban el error (proveedor devolvía la entidad sin
 * guardar) o lo cambiaban por «No se pudo guardar», y solo reconocían el duplicado si el nombre
 * del constraint estaba en el mensaje de afuera. Al fallar en el commit, el nombre solo está en la
 * causa raíz.
 */
class GuardarConConstraintUnicoTest {

    static Stream<Arguments> resolvers() {
        return Stream.of(
                caso(new ProveedorGraphQL(), r -> ((ProveedorGraphQL) r).saveProveedor(proveedorInput()),
                        "proveedor_un", "Esta persona ya es un proveedor"),
                caso(new ProveedorServicioGraphQL(), r -> ((ProveedorServicioGraphQL) r).saveProveedorServicio(new ProveedorServicioInput()),
                        "proveedor_servicio_un", "Esta persona ya es un proveedor de servicio"),
                caso(new CajaCategoriaObservacionGraphQL(), r -> ((CajaCategoriaObservacionGraphQL) r).saveCajaCategoriaObservacion(new CajaCategoriaObservacionInput()),
                        "caja_categoria_observacion_unique", "Ya existe una categoria con ese nombre."),
                caso(new CajaSubCategoriaObservacionGraphQL(), r -> ((CajaSubCategoriaObservacionGraphQL) r).saveCajaSubCategoriaObservacion(new CajaSubCategoriaObservacionInput()),
                        "caja_subcategoria_observacion_unique", "Ya existe una subcategoria con ese nombre"),
                caso(new CajaMotivoObservacionGraphQL(), r -> ((CajaMotivoObservacionGraphQL) r).saveCajaMotivoObservacion(new CajaMotivoObservacionInput()),
                        "caja_motivo_observacion_unique", "Ya existe un motivo con esa descripción"),
                caso(new CategoriaObservacionGraphQL(), r -> ((CategoriaObservacionGraphQL) r).saveCategoriaObservacion(new CategoriaObservacionInput()),
                        "categoria_observacion_unique", "Ya existe una categoria con ese nombre."),
                caso(new SubcategoriaObservacionGraphQL(), r -> ((SubcategoriaObservacionGraphQL) r).saveSubcategoriaObservacion(new SubcategoriaObservacionInput()),
                        "subcategoria_observacion_unique", "Ya existe una subcategoria con ese nombre"),
                caso(new MotivoObservacionGraphQL(), r -> ((MotivoObservacionGraphQL) r).saveMotivoObservacion(new MotivoObservacionInput()),
                        "motivo_observacion_unique", "Ya existe un motivo con esa descripción")
        );
    }

    private static Arguments caso(Object resolver, Function<Object, Object> guardar, String constraint, String mensaje) {
        return Arguments.of(resolver.getClass().getSimpleName(), resolver, guardar, constraint, mensaje);
    }

    private static ProveedorInput proveedorInput() {
        ProveedorInput input = new ProveedorInput();
        input.setPersonaId(5L);
        input.setUsuarioId(1L);
        return input;
    }

    /** Un mock por cada @Autowired; devuelve el del campo `service`. */
    @SuppressWarnings("unchecked")
    private static CrudService<Object, ?, ?> conMocks(Object resolver) {
        for (Field f : resolver.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(Autowired.class)) ReflectionTestUtils.setField(resolver, f.getName(), mock(f.getType()));
        }
        return (CrudService<Object, ?, ?>) ReflectionTestUtils.getField(resolver, "service");
    }

    /** Lo que llega de Spring cuando el UNIQUE salta al hacer flush en el commit: el nombre solo en la raíz. */
    private static RuntimeException duplicadoEnElCommit(String constraint) {
        PSQLException psql = new PSQLException(
                "ERROR: duplicate key value violates unique constraint \"" + constraint + "\"\n  Detail: Key (descripcion)=(X) already exists.",
                PSQLState.UNIQUE_VIOLATION);
        ConstraintViolationException hibernate = new ConstraintViolationException("could not execute statement", psql, constraint);
        return new TransactionSystemException("Could not commit JPA transaction",
                new RollbackException("Error while committing the transaction", hibernate));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolvers")
    void elDuplicadoEnLaCausaDaElMensajeDeNegocio(String nombre, Object resolver, Function<Object, Object> guardar,
                                                  String constraint, String mensaje) {
        when(conMocks(resolver).save(any())).thenThrow(duplicadoEnElCommit(constraint));

        GraphQLException ex = assertThrows(GraphQLException.class, () -> guardar.apply(resolver));
        assertEquals(mensaje, ex.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolvers")
    void otroErrorSeRelanzaTalCual(String nombre, Object resolver, Function<Object, Object> guardar,
                                   String constraint, String mensaje) {
        RuntimeException original = new DataIntegrityViolationException("not-null property references a null or transient value");
        when(conMocks(resolver).save(any())).thenThrow(original);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> guardar.apply(resolver));
        assertSame(original, ex);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolvers")
    void unErrorSinMensajeSeRelanzaTalCual(String nombre, Object resolver, Function<Object, Object> guardar,
                                           String constraint, String mensaje) {
        RuntimeException original = new IllegalStateException((String) null);
        when(conMocks(resolver).save(any())).thenThrow(original);

        assertSame(original, assertThrows(RuntimeException.class, () -> guardar.apply(resolver)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolvers")
    void elGuardadoOkDevuelveLoQueGuardoElServicio(String nombre, Object resolver, Function<Object, Object> guardar,
                                                   String constraint, String mensaje) {
        when(conMocks(resolver).save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(guardar.apply(resolver));
    }
}
