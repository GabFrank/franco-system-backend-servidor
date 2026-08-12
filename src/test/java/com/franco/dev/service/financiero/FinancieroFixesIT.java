package com.franco.dev.service.financiero;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT e2e de los fixes de integracion tesoreria/compras contra la DB dev real.
 * Llama a los servicios/resolvers directamente (sin auth/GraphQL HTTP) y valida
 * efectos reales sobre la DB: nota.pagado, aging CPP, filtro por tipo, guard proveedor nulo.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true.
 * Cada test es @Transactional -> rollback automatico, no ensucia la DB dev.
 * Si la DB no tiene datos de referencia, el test se salta (assumeTrue) en vez de fallar.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=FinancieroFixesIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class FinancieroFixesIT {

    @Autowired private SolicitudPagoService solicitudPagoService;
    @Autowired private SolicitudPagoNotaRecepcionService solicitudPagoNotaRecepcionService;
    @Autowired private TesoreriaReporteService tesoreriaReporteService;
    @Autowired private com.franco.dev.repository.operaciones.SolicitudPagoRepository solicitudPagoRepository;
    @PersistenceContext private EntityManager em;

    private Moneda anyMoneda() {
        List<Moneda> monedas = em.createQuery("select m from Moneda m", Moneda.class).setMaxResults(1).getResultList();
        return monedas.isEmpty() ? null : monedas.get(0);
    }

    /** Nota sin pagar, con pedido->proveedor, y NO vinculada a ninguna solicitud. */
    private Object[] notaLibreConProveedor() {
        List<Object[]> rows = em.createQuery(
                "select n.id, n.pedido.proveedor.id from NotaRecepcion n " +
                "where (n.pagado is null or n.pagado = false) and n.pedido.proveedor is not null " +
                "and not exists (select r.id from SolicitudPagoNotaRecepcion r where r.notaRecepcion.id = n.id)",
                Object[].class).setMaxResults(5).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SolicitudPago crear(TipoSolicitudPago tipo, SolicitudPagoEstado estado, Proveedor proveedor,
                                Moneda moneda, double montoTotal, LocalDateTime fechaPagoPropuesta, String numero) {
        SolicitudPago sp = new SolicitudPago();
        sp.setTipo(tipo);
        sp.setEstado(estado);
        sp.setProveedor(proveedor);
        sp.setMoneda(moneda);
        sp.setMontoTotal(montoTotal);
        sp.setMontoPagado(BigDecimal.ZERO);
        sp.setNumeroSolicitud(numero);
        sp.setFechaSolicitud(LocalDateTime.now());
        sp.setFechaPagoPropuesta(fechaPagoPropuesta);
        SolicitudPago saved = solicitudPagoService.save(sp);
        em.flush();
        return saved;
    }

    /** FIX #1 y #1b: concluir marca la nota como pagada; anular/desmarcar la revierte (efecto real en DB). */
    @Test
    void concluir_marca_nota_pagada_y_desmarcar_la_revierte() {
        Moneda moneda = anyMoneda();
        Object[] nota = notaLibreConProveedor();
        assumeTrue(moneda != null && nota != null, "Sin moneda o nota libre con proveedor en la DB dev");
        Long notaId = (Long) nota[0];
        Long provId = (Long) nota[1];
        Proveedor prov = em.find(Proveedor.class, provId);

        SolicitudPago sp = crear(TipoSolicitudPago.COMPRA, SolicitudPagoEstado.SOLICITADO, prov, moneda,
                100000.0, LocalDateTime.now().plusDays(5), "IT-CPP-CONCLUIR");
        solicitudPagoNotaRecepcionService.agregarNotaASolicitud(sp.getId(), notaId, 100000.0);
        em.flush();

        // Concluir via el mismo metodo al que FIX #1 delega desde el pago CPP.
        solicitudPagoService.actualizarEstado(sp.getId(), SolicitudPagoEstado.CONCLUIDO);
        em.flush();
        em.clear();
        Boolean pagadoDespues = em.createQuery(
                "select n.pagado from NotaRecepcion n where n.id = :id", Boolean.class)
                .setParameter("id", notaId).getSingleResult();
        assertTrue(Boolean.TRUE.equals(pagadoDespues), "FIX #1: la nota debe quedar pagada al concluir");

        // Desmarcar (lo que hace anularPagoCpp al reabrir) revierte.
        solicitudPagoService.desmarcarNotasComoPagadas(sp.getId());
        em.flush();
        em.clear();
        Boolean pagadoRevertido = em.createQuery(
                "select n.pagado from NotaRecepcion n where n.id = :id", Boolean.class)
                .setParameter("id", notaId).getSingleResult();
        assertFalse(Boolean.TRUE.equals(pagadoRevertido), "FIX #1b: la nota debe volver a no pagada al anular");
    }

    private static BigDecimal saldo(SolicitudPago sp) {
        BigDecimal total = BigDecimal.valueOf(sp.getMontoTotal() != null ? sp.getMontoTotal() : 0.0);
        BigDecimal pagado = sp.getMontoPagado() != null ? sp.getMontoPagado() : BigDecimal.ZERO;
        return total.subtract(pagado);
    }

    /**
     * FIX #2 (datos reales): agingCpp suma la deuda abierta {SOLICITADO, PARCIAL},
     * NO la lista vieja {PENDIENTE, PARCIAL}. Demuestra el bug: los borradores PENDIENTE
     * (que no son deuda) quedaban contados y el SOLICITADO real quedaba fuera.
     */
    @Test
    void aging_cpp_usa_deuda_abierta_no_pendiente() {
        BigDecimal saldoAbierta = solicitudPagoRepository.findByEstadoIn(SolicitudPagoEstado.DEUDA_ABIERTA)
                .stream().map(FinancieroFixesIT::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoViejo = solicitudPagoRepository.findByEstadoIn(
                java.util.Arrays.asList(SolicitudPagoEstado.PENDIENTE, SolicitudPagoEstado.PARCIAL))
                .stream().map(FinancieroFixesIT::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        assumeTrue(saldoAbierta.compareTo(saldoViejo) != 0,
                "La DB dev no distingue deuda abierta vs semantica vieja; el test no discriminaria el fix");

        TesoreriaReporteService.Aging a = tesoreriaReporteService.agingCpp();
        BigDecimal total = a.getVencido().add(a.getPorVencer());

        assertEquals(0, total.compareTo(saldoAbierta),
                "FIX #2: aging debe sumar la deuda abierta (SOLICITADO+PARCIAL)");
        assertNotEquals(0, total.compareTo(saldoViejo),
                "FIX #2: aging NO debe seguir la semantica vieja (PENDIENTE+PARCIAL)");
    }

    /** FIX #3: agregar una nota a una solicitud GASTO (proveedor nulo) no lanza NPE. */
    @Test
    void agregar_nota_a_gasto_sin_proveedor_no_npe() {
        Moneda moneda = anyMoneda();
        Object[] nota = notaLibreConProveedor();
        assumeTrue(moneda != null && nota != null, "Sin moneda o nota libre con proveedor en la DB dev");
        Long notaId = (Long) nota[0];

        SolicitudPago gasto = crear(TipoSolicitudPago.GASTO, SolicitudPagoEstado.PENDIENTE, null, moneda,
                0.0, null, "IT-GASTO-NOTA");
        // proveedor de la solicitud es null; el guard debe saltar la validacion de "mismo proveedor".
        SolicitudPagoNotaRecepcion rel = assertDoesNotThrow(
                () -> solicitudPagoNotaRecepcionService.agregarNotaASolicitud(gasto.getId(), notaId, 1000.0));
        assertNotNull(rel);
        assertEquals(1, solicitudPagoNotaRecepcionService.getNotasDeSolicitud(gasto.getId()).size());
    }
}
