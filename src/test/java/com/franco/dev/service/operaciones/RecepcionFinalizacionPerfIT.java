package com.franco.dev.service.operaciones;

import com.franco.dev.graphql.operaciones.RecepcionMercaderiaItemGraphQL;
import com.franco.dev.dto.operaciones.ValidacionFinalizacionRecepcion;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Medicion de validarFinalizacionRecepcionPorPedido contra la DB dev real.
 * Reporta tiempo y cantidad de sentencias SQL por pedido, que es lo que decide
 * si el N+1 del loop de items pesa o no.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.perf=true.
 * Es read-only y @Transactional -> rollback automatico.
 *
 * Correr:  ./mvnw -Dit.perf=true -Dtest=RecepcionFinalizacionPerfIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.perf", matches = "true")
class RecepcionFinalizacionPerfIT {

    @Autowired private RecepcionMercaderiaItemGraphQL resolver;

    /** SecurityGraphQLAspect exige un Authentication no anonimo para llamar cualquier resolver. */
    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("perf-it", null,
                        java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Autowired private EntityManagerFactory emf;
    @PersistenceContext private EntityManager em;

    /** Pedidos con notas, del mas grande al mas chico, para ver como escala. */
    private List<Object[]> pedidosParaMedir(int cantidad) {
        return em.createQuery(
                "select n.pedido.id, count(i.id) from NotaRecepcionItem i " +
                "join i.notaRecepcion n " +
                "group by n.pedido.id order by count(i.id) desc",
                Object[].class).setMaxResults(cantidad).getResultList();
    }

    private List<Long> sucursalesDe(Long pedidoId) {
        return em.createQuery(
                "select distinct d.sucursalEntrega.id from NotaRecepcionItemDistribucion d " +
                "where d.notaRecepcionItem.notaRecepcion.pedido.id = :pedidoId",
                Long.class).setParameter("pedidoId", pedidoId).getResultList();
    }

    @Test
    void medirValidacionFinalizacion() {
        List<Object[]> pedidos = pedidosParaMedir(5);
        assumeTrue(!pedidos.isEmpty(), "La DB dev no tiene notas de recepcion");

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        List<String> filas = new ArrayList<>();
        for (Object[] fila : pedidos) {
            Long pedidoId = ((Number) fila[0]).longValue();
            long items = ((Number) fila[1]).longValue();
            List<Long> sucursales = sucursalesDe(pedidoId);
            if (sucursales.isEmpty()) continue;

            em.flush();
            em.clear();
            stats.clear();
            long t0 = System.nanoTime();
            ValidacionFinalizacionRecepcion r =
                    resolver.validarFinalizacionRecepcionPorPedido(pedidoId, sucursales);
            long ms = (System.nanoTime() - t0) / 1_000_000;

            filas.add(String.format(
                    "pedido=%d items=%d sucursales=%d -> %d ms, %d sentencias SQL, %d entidades cargadas, puedeFinalizar=%s, pendientes=%d",
                    pedidoId, items, sucursales.size(), ms,
                    stats.getPrepareStatementCount(), stats.getEntityLoadCount(),
                    r.getPuedeFinalizar(), r.getCantidadItemsPendientes()));
        }

        System.out.println("=== MEDICION validarFinalizacionRecepcionPorPedido ===");
        filas.forEach(System.out::println);
    }
}
