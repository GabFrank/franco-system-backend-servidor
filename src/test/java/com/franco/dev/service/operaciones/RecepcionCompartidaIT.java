package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.graphql.operaciones.RecepcionMercaderiaItemGraphQL;
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
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Recepción física compartida entre pedidos del mismo proveedor/sucursal/día/usuario.
 * Corre contra la DB dev real (bodega3) con rollback: no ensucia nada.
 *
 * Correr:  ./mvnw -o -Dit.planificacion=true -Dtest=RecepcionCompartidaIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.planificacion", matches = "true")
class RecepcionCompartidaIT {

    @Autowired private RecepcionMercaderiaItemGraphQL graphQL;
    @Autowired private RecepcionMercaderiaService recepcionMercaderiaService;
    @Autowired private ProcesoEtapaService procesoEtapaService;
    @PersistenceContext private EntityManager em;

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("recepcion-compartida-it", null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private ProcesoEtapaEstado recepcionMercaderia(Long pedidoId) {
        em.flush();
        em.clear();
        return procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.RECEPCION_MERCADERIA)
                .orElseThrow().getEstadoEtapa();
    }

    /** Pedidos (con ítems en la recepción) de una recepción abierta; vacío si no hay ninguna. */
    @SuppressWarnings("unchecked")
    private List<Object[]> recepcionesAbiertasConPedidos() {
        return em.createNativeQuery(
                "select ri.recepcion_mercaderia_id, n.pedido_id, ri.sucursal_entrega_id " +
                "from operaciones.recepcion_mercaderia_item ri " +
                "join operaciones.recepcion_mercaderia rm on rm.id = ri.recepcion_mercaderia_id " +
                "join operaciones.nota_recepcion_item i on i.id = ri.nota_recepcion_item_id " +
                "join operaciones.nota_recepcion n on n.id = i.nota_recepcion_id " +
                "where rm.estado in ('EN_PROCESO', 'PENDIENTE') " +
                "group by 1, 2, 3 order by 1 desc").getResultList();
    }

    @Test
    void recepcionAbierta_soloSeReutilizaParaElMismoPedido() {
        List<Object[]> filas = recepcionesAbiertasConPedidos();
        // Una recepción de un solo pedido (las ya compartidas no se reutilizan para ninguno)
        Object[] fila = filas.stream()
                .filter(f -> filas.stream().filter(x -> ((Number) x[0]).longValue() == ((Number) f[0]).longValue())
                        .map(x -> ((Number) x[1]).longValue()).distinct().count() == 1)
                .findFirst().orElse(null);
        assumeTrue(fila != null, "No hay recepciones abiertas de un solo pedido en la DB dev");
        Long recepcionId = ((Number) fila[0]).longValue();
        Long pedidoId = ((Number) fila[1]).longValue();
        RecepcionMercaderia rm = recepcionMercaderiaService.findById(recepcionId).orElseThrow();

        List<Long> mismoPedido = recepcionMercaderiaService.findRecepcionesReutilizables(
                rm.getProveedor().getId(), rm.getSucursalRecepcion().getId(), rm.getFecha(),
                rm.getUsuario().getId(), pedidoId).stream().map(RecepcionMercaderia::getId).collect(Collectors.toList());
        List<Long> otroPedido = recepcionMercaderiaService.findRecepcionesReutilizables(
                rm.getProveedor().getId(), rm.getSucursalRecepcion().getId(), rm.getFecha(),
                rm.getUsuario().getId(), -1L).stream().map(RecepcionMercaderia::getId).collect(Collectors.toList());

        assertTrue(mismoPedido.contains(recepcionId), "El mismo pedido reutiliza su recepción abierta");
        assertFalse(otroPedido.contains(recepcionId), "Otro pedido no debe colgarse de una recepción ajena");
    }

    /**
     * Recepción ya compartida por dos pedidos (datos previos al arreglo): finalizar uno cierra la
     * recepción entera; el otro tiene que poder completar su etapa en vez de quedar trabado.
     */
    @Test
    void recepcionYaCompartida_finalizarAmbosPedidosCompletaLasDosEtapas() {
        List<Object[]> filas = recepcionesAbiertasConPedidos();
        Long recepcionId = null;
        List<Long> pedidos = null;
        Long sucursalId = null;
        for (Object[] f : filas) {
            Long rm = ((Number) f[0]).longValue();
            List<Long> ps = filas.stream().filter(x -> ((Number) x[0]).longValue() == rm)
                    .map(x -> ((Number) x[1]).longValue()).distinct().collect(Collectors.toList());
            if (ps.size() > 1) {
                recepcionId = rm;
                pedidos = ps;
                sucursalId = ((Number) f[2]).longValue();
                break;
            }
        }
        assumeTrue(recepcionId != null, "No hay recepciones compartidas en la DB dev");
        List<Long> sucursales = Collections.singletonList(sucursalId);

        for (Long pedidoId : pedidos) {
            assumeTrue(graphQL.validarFinalizacionRecepcionPorPedido(pedidoId, sucursales).getPuedeFinalizar(),
                    "El pedido " + pedidoId + " tiene ítems sin verificar");
        }

        for (Long pedidoId : pedidos) {
            assertTrue(graphQL.finalizarRecepcionFisicaPorPedido(pedidoId, sucursales));
            assertEquals(ProcesoEtapaEstado.COMPLETADA, recepcionMercaderia(pedidoId),
                    "RECEPCION_MERCADERIA del pedido " + pedidoId + " debe quedar COMPLETADA");
        }
    }
}
