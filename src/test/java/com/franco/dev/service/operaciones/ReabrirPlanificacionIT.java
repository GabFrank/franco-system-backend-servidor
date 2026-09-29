package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.PedidoResumen;
import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reproduce el bug "al finalizar la planificación no aparece Reabrir Planificación".
 * Corre contra la DB dev real (bodega3) con rollback: no ensucia nada.
 *
 * Correr:  ./mvnw -Dit.planificacion=true -Dtest=ReabrirPlanificacionIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.planificacion", matches = "true")
class ReabrirPlanificacionIT {

    @Autowired private PedidoService pedidoService;
    @Autowired private ProcesoEtapaService procesoEtapaService;
    @Autowired private NotaRecepcionService notaRecepcionService;
    @PersistenceContext private EntityManager em;

    /** Pedido en planificación (CREACION EN_PROCESO, RECEPCION_NOTA PENDIENTE) con ítems y sin notas. */
    private Pedido pedidoEnPlanificacion() {
        List<Long> ids = em.createQuery(
                "select c.pedido.id from ProcesoEtapa c, ProcesoEtapa r " +
                "where c.pedido.id = r.pedido.id " +
                "and c.tipoEtapa = :creacion and c.estadoEtapa = :enProceso " +
                "and r.tipoEtapa = :recNota and r.estadoEtapa = :pendiente " +
                "and exists (select i.id from PedidoItem i where i.pedido.id = c.pedido.id) " +
                "and not exists (select n.id from NotaRecepcion n where n.pedido.id = c.pedido.id) " +
                "order by c.pedido.id desc", Long.class)
                .setParameter("creacion", ProcesoEtapaTipo.CREACION)
                .setParameter("recNota", ProcesoEtapaTipo.RECEPCION_NOTA)
                .setParameter("enProceso", ProcesoEtapaEstado.EN_PROCESO)
                .setParameter("pendiente", ProcesoEtapaEstado.PENDIENTE)
                .setMaxResults(1).getResultList();
        assumeTrue(!ids.isEmpty(), "No hay pedidos en planificación con ítems en la DB dev");
        return pedidoService.findById(ids.get(0)).orElseThrow();
    }

    private ProcesoEtapaEstado estado(Long pedidoId, ProcesoEtapaTipo tipo) {
        em.flush();
        em.clear();
        return procesoEtapaService.getEtapaByPedidoAndTipo(pedidoId, tipo).orElseThrow().getEstadoEtapa();
    }

    private NotaRecepcion nuevaNota(Pedido pedido) {
        NotaRecepcion n = new NotaRecepcion();
        n.setPedido(pedido);
        n.setNumero(987654321);
        n.setMoneda(pedido.getMoneda());
        n.setEstado(NotaRecepcionEstado.PENDIENTE_CONCILIACION);
        n.setTipoBoleta("FACTURA");
        n.setFecha(LocalDateTime.now());
        n.setCreadoEn(LocalDateTime.now());
        return notaRecepcionService.save(n);
    }

    @Test
    void finalizar_dejaRecepcionNotaPendiente_yPermiteReabrir() {
        Pedido pedido = pedidoEnPlanificacion();

        pedidoService.finalizarCreacion(pedido.getId());

        assertEquals(ProcesoEtapaEstado.COMPLETADA, estado(pedido.getId(), ProcesoEtapaTipo.CREACION));
        assertEquals(ProcesoEtapaEstado.PENDIENTE, estado(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA),
                "Al finalizar, RECEPCION_NOTA debe quedar PENDIENTE");

        procesoEtapaService.revertirEtapaCreacion(pedido.getId());
        assertEquals(ProcesoEtapaEstado.EN_PROCESO, estado(pedido.getId(), ProcesoEtapaTipo.CREACION));
    }

    /** Lo que el frontend usa para el encabezado: etapaActual del resumen. */
    @Test
    void resumen_trasFinalizar_devuelveRecepcionNotaPeroPendiente() {
        Pedido pedido = pedidoEnPlanificacion();
        pedidoService.finalizarCreacion(pedido.getId());
        em.flush();
        em.clear();

        PedidoResumen resumen = pedidoService.getPedidoResumen(pedido.getId());
        ProcesoEtapa etapaActual = resumen.getEtapaActual();
        assertEquals(ProcesoEtapaTipo.RECEPCION_NOTA, etapaActual.getTipoEtapa());
        assertEquals(ProcesoEtapaEstado.PENDIENTE, etapaActual.getEstadoEtapa(),
                "El resumen dice RECEPCION_NOTA/PENDIENTE; el frontend lo pinta como 'EN RECEPCIÓN NOTAS'");
    }

    /**
     * Con notas cargadas (RECEPCION_NOTA EN_PROCESO) también se puede reabrir: la etapa vuelve
     * a PENDIENTE y, al finalizar de nuevo, como el pedido ya tiene notas arranca EN_PROCESO.
     */
    @Test
    void conNotaCargada_sePuedeReabrir_yAlRefinalizarVuelveAEnProceso() {
        Pedido pedido = pedidoEnPlanificacion();
        pedidoService.finalizarCreacion(pedido.getId());
        nuevaNota(pedido);
        assertEquals(ProcesoEtapaEstado.EN_PROCESO, estado(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA));

        procesoEtapaService.revertirEtapaCreacion(pedido.getId());

        assertEquals(ProcesoEtapaEstado.EN_PROCESO, estado(pedido.getId(), ProcesoEtapaTipo.CREACION));
        assertEquals(ProcesoEtapaEstado.PENDIENTE, estado(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA));

        pedidoService.finalizarCreacion(pedido.getId());

        assertEquals(ProcesoEtapaEstado.EN_PROCESO, estado(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA),
                "El pedido ya tiene notas: la recepción documental retoma EN_PROCESO");
    }

    /** Si la recepción física ya empezó, reabrir sigue bloqueado. */
    @Test
    void conRecepcionMercaderiaIniciada_noSePuedeReabrir() {
        Pedido pedido = pedidoEnPlanificacion();
        pedidoService.finalizarCreacion(pedido.getId());
        nuevaNota(pedido);
        procesoEtapaService.actualizarEtapaAEnProceso(pedido.getId(), ProcesoEtapaTipo.RECEPCION_MERCADERIA);

        assertThrows(IllegalStateException.class, () -> procesoEtapaService.revertirEtapaCreacion(pedido.getId()));
    }

    /**
     * Caso "a veces": se carga una nota y se la borra. El pedido vuelve a no tener notas,
     * pero RECEPCION_NOTA queda EN_PROCESO para siempre y la planificación ya no se puede reabrir.
     */
    @Test
    void borrarUnicaNota_devuelveRecepcionNotaAPendiente_yPermiteReabrir() {
        Pedido pedido = pedidoEnPlanificacion();
        pedidoService.finalizarCreacion(pedido.getId());
        NotaRecepcion nota = nuevaNota(pedido);
        em.flush();

        assertTrue(notaRecepcionService.deleteById(nota.getId()));
        em.flush();
        em.clear();
        assertTrue(notaRecepcionService.findByPedidoId(pedido.getId()).isEmpty());

        assertEquals(ProcesoEtapaEstado.PENDIENTE, estado(pedido.getId(), ProcesoEtapaTipo.RECEPCION_NOTA),
                "Sin notas, RECEPCION_NOTA debería volver a PENDIENTE");
        assertDoesNotThrow(() -> procesoEtapaService.revertirEtapaCreacion(pedido.getId()));
    }
}
