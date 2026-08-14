package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La cola de despacho no puede ordenarse por fechaEnvio: esa columna es NULL en
 * toda fila PENDIENTE, que es justamente lo que la query selecciona. Ordenar por
 * una clave constante fuerza a ordenar la cola completa para devolver un lote.
 */
class NotificacionEnvioLogRepositoryOrdenTest {

    private String jpqlDeFindBatchByEstado() throws NoSuchMethodException {
        Method metodo = NotificacionEnvioLogRepository.class
                .getMethod("findBatchByEstado", EstadoEnvio.class, Pageable.class);
        Query query = metodo.getAnnotation(Query.class);
        assertNotNull(query, "findBatchByEstado debe declarar @Query");
        return query.value();
    }

    @Test
    void laColaDeDespachoNoSeOrdenaPorFechaEnvio() throws NoSuchMethodException {
        assertFalse(jpqlDeFindBatchByEstado().contains("ORDER BY nel.fechaEnvio"),
                "fechaEnvio es NULL en las filas PENDIENTE: ordenar por esa columna degrada el despacho a seq scan");
    }

    @Test
    void laColaDeDespachoEsFifoPorId() throws NoSuchMethodException {
        assertTrue(jpqlDeFindBatchByEstado().contains("ORDER BY nel.id ASC"),
                "el lote debe salir FIFO por id, que es recorrible por indice");
    }

    @Test
    void laColaSigueFiltrandoPorEstado() throws NoSuchMethodException {
        assertTrue(jpqlDeFindBatchByEstado().contains("nel.estadoEnvio = :estado"),
                "el filtro por estado no debe perderse al cambiar el orden");
    }
}
