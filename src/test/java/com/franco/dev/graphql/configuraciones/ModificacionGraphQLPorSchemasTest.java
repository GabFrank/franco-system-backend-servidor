package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionRegistro;
import com.franco.dev.repository.configuraciones.ModificacionRegistroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Modificaciones del sistema: varios esquemas paginados juntos, con orden fijo (desktop #583). */
class ModificacionGraphQLPorSchemasTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 10, 23, 23, 59);

    private ModificacionRegistroRepository repository;
    private ModificacionGraphQL resolver;

    @BeforeEach
    void preparar() {
        repository = mock(ModificacionRegistroRepository.class);
        resolver = new ModificacionGraphQL();
        ReflectionTestUtils.setField(resolver, "registroRepository", repository);
    }

    @Test
    void paginaLosEsquemasJuntosConLaPaginaPedida() {
        List<String> schemas = Arrays.asList("productos", "operaciones");
        List<ModificacionRegistro> filas = Arrays.asList(new ModificacionRegistro(), new ModificacionRegistro());
        when(repository.findBySchemaNombreInAndFechaBetween(eq(schemas), eq(INICIO), eq(FIN), any()))
                .thenReturn(new PageImpl<>(filas, PageRequest.of(3, 2), 120));

        ModificacionGraphQL.ModificacionRegistroPage res = resolver.modificacionesPorSchemas(schemas, INICIO, FIN, 3, 2);

        verify(repository).findBySchemaNombreInAndFechaBetween(schemas, INICIO, FIN, PageRequest.of(3, 2));
        assertEquals(filas, res.getContent());
        assertEquals(120, res.getTotalElements());
        assertEquals(60, res.getTotalPages());
        assertEquals(3, res.getPageNumber());
        assertEquals(2, res.getPageSize());
    }

    @Test
    void sinEsquemasDevuelveUnaPaginaVaciaSinConsultar() {
        ModificacionGraphQL.ModificacionRegistroPage res =
                resolver.modificacionesPorSchemas(Collections.emptyList(), INICIO, FIN, 0, 15);

        verifyNoInteractions(repository);
        assertTrue(res.getContent().isEmpty());
        assertEquals(0, res.getTotalElements());
    }

    /** Con fechas iguales, sin desempate el orden cambia entre consultas y una fila salta o se repite al paginar. */
    @Test
    void lasConsultasPaginadasDesempatanPorId() throws NoSuchMethodException {
        Class<ModificacionRegistroRepository> repo = ModificacionRegistroRepository.class;
        Method[] paginadas = {
                repo.getMethod("findBySchemaNombreAndFechaBetween", String.class, LocalDateTime.class, LocalDateTime.class, Pageable.class),
                repo.getMethod("findBySchemaNombreInAndFechaBetween", List.class, LocalDateTime.class, LocalDateTime.class, Pageable.class),
                repo.getMethod("findByTipoEntidad", String.class, Pageable.class),
                repo.getMethod("findByTipoEntidadAndSchemaAndFechaBetween", String.class, String.class, LocalDateTime.class, LocalDateTime.class, Pageable.class),
        };
        for (Method m : paginadas) {
            String jpql = m.getAnnotation(Query.class).value();
            assertTrue(jpql.endsWith("ORDER BY m.modificadoEn DESC, m.id DESC"), m.getName() + ": " + jpql);
        }
    }
}
