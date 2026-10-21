package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoDetalleInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de #435: el alta y la edición de una solicitud de pago se guardan enteras o nada. Si fallan las formas de
 * pago, no queda la solicitud con la nota tomada (el reintento decía «ya está incluida en otra solicitud») ni una
 * edición a medias.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.financiero=true. No es @Transactional: necesita commits reales.
 *
 * Correr:  ./mvnw -Dit.financiero=true -Dtest=SolicitudPagoAtomicidadIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"dev", "user-dev"})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.financiero", matches = "true")
class SolicitudPagoAtomicidadIT {

    private static final long MONEDA_INEXISTENTE = 999_999_999L;

    @Autowired private SolicitudPagoGraphQL graphQL;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private TransactionTemplate tx;
    private Long notaId;
    private Integer proveedorId;
    private Long monedaId;
    private Long formaPagoId;
    private String marca;
    private List<?> etapa;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Una nota de un pedido con proveedor que no esté en ninguna solicitud.
        List<?> notas = tx.execute(s -> em.createNativeQuery(
                "select nr.id, p.proveedor_id from operaciones.nota_recepcion nr"
                        + " join operaciones.pedido p on p.id = nr.pedido_id"
                        + " where p.proveedor_id is not null and not exists (select 1 from"
                        + " operaciones.solicitud_pago_nota_recepcion r where r.nota_recepcion_id = nr.id)"
                        + " order by nr.id desc").setMaxResults(1).getResultList());
        List<?> monedas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.moneda order by id").setMaxResults(1).getResultList());
        List<?> formas = tx.execute(s -> em.createNativeQuery(
                "select id from financiero.forma_pago order by id").setMaxResults(1).getResultList());
        assumeTrue(!notas.isEmpty() && !monedas.isEmpty() && !formas.isEmpty(),
                "la base no tiene una nota libre, moneda o forma de pago");
        Object[] nota = (Object[]) notas.get(0);
        notaId = ((Number) nota[0]).longValue();
        proveedorId = ((Number) nota[1]).intValue();
        monedaId = ((Number) monedas.get(0)).longValue();
        formaPagoId = ((Number) formas.get(0)).longValue();
        marca = "IT ATOMICIDAD SP " + System.nanoTime();
        // El alta pasa la etapa SOLICITUD_PAGO del pedido a EN_PROCESO: se guarda para devolverla al terminar.
        etapa = tx.execute(s -> em.createNativeQuery(
                "select e.id, cast(e.estado_etapa as text), e.fecha_inicio from operaciones.proceso_etapa e"
                        + " join operaciones.nota_recepcion nr on nr.pedido_id = e.pedido_id"
                        + " where nr.id = :n and cast(e.tipo_etapa as text) = 'SOLICITUD_PAGO'")
                .setParameter("n", notaId).getResultList());
        // SecurityGraphQLAspect exige un usuario autenticado en cada mutation.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("it-solicitud-pago", "n/a", Collections.emptyList()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        tx.execute(s -> {
            String ids = "(select id from operaciones.solicitud_pago where observaciones like :m)";
            em.createNativeQuery("delete from operaciones.solicitud_pago_detalle where solicitud_pago_id in " + ids)
                    .setParameter("m", marca + "%").executeUpdate();
            em.createNativeQuery("delete from operaciones.solicitud_pago_nota_recepcion where solicitud_pago_id in " + ids)
                    .setParameter("m", marca + "%").executeUpdate();
            em.createNativeQuery("delete from operaciones.solicitud_pago where observaciones like :m")
                    .setParameter("m", marca + "%").executeUpdate();
            for (Object fila : etapa) {
                Object[] e = (Object[]) fila;
                // Un null sin tipo no se puede castear a timestamp desde Hibernate: se escribe literal.
                String inicio = e[2] == null ? "null" : "cast('" + e[2] + "' as timestamp)";
                em.createNativeQuery("update operaciones.proceso_etapa set estado_etapa = cast(:estado as "
                                + "operaciones.proceso_etapa_estado), fecha_inicio = " + inicio + " where id = :id")
                        .setParameter("estado", e[1]).setParameter("id", e[0])
                        .executeUpdate();
            }
            return null;
        });
    }

    private SolicitudPagoDetalleInput detalle(Long moneda, double valor) {
        SolicitudPagoDetalleInput d = new SolicitudPagoDetalleInput();
        d.setMonedaId(moneda);
        d.setFormaPagoId(formaPagoId);
        d.setValor(valor);
        return d;
    }

    private SolicitudPagoGraphQL.SolicitudPagoInput alta(List<SolicitudPagoDetalleInput> detalles) {
        SolicitudPagoGraphQL.SolicitudPagoInput in = new SolicitudPagoGraphQL.SolicitudPagoInput();
        in.setProveedorId(proveedorId);
        in.setNotaRecepcionIds(Collections.singletonList(notaId));
        in.setObservaciones(marca);
        in.setDetalles(detalles);
        return in;
    }

    private long contar(String sql, Object param) {
        return tx.execute(s -> ((Number) em.createNativeQuery(sql).setParameter("p", param).getSingleResult()).longValue());
    }

    @Test
    void siFallanLasFormasDePagoElAltaNoDejaNadaYElReintentoEntra() {
        // El primer detalle es válido (de él sale la moneda de la cabecera); el segundo falla en sincronizarDetalles.
        List<SolicitudPagoDetalleInput> conError = Arrays.asList(detalle(monedaId, 1.0), detalle(MONEDA_INEXISTENTE, 1.0));

        RuntimeException e = assertThrows(RuntimeException.class, () -> graphQL.saveSolicitudPago(alta(conError)));
        // Que falle por la moneda del segundo detalle, no por otra cosa (y que el mensaje llegue con su prefijo).
        assertTrue(e.getMessage().startsWith("Error al crear solicitud de pago: Moneda no encontrada"), e.getMessage());

        assertEquals(0, contar("select count(*) from operaciones.solicitud_pago where observaciones = :p", marca));
        assertEquals(0, contar("select count(*) from operaciones.solicitud_pago_nota_recepcion where nota_recepcion_id = :p", notaId));

        SolicitudPago creada = graphQL.saveSolicitudPago(alta(Collections.singletonList(detalle(monedaId, 1.0))));
        assertEquals(1, contar("select count(*) from operaciones.solicitud_pago_detalle where solicitud_pago_id = :p", creada.getId()));
    }

    @Test
    void siFallanLasFormasDePagoLaEdicionDejaLaSolicitudComoEstaba() {
        SolicitudPago creada = graphQL.saveSolicitudPago(alta(Collections.singletonList(detalle(monedaId, 1.0))));

        SolicitudPagoGraphQL.SolicitudPagoInput edicion = alta(Arrays.asList(detalle(monedaId, 2.0), detalle(MONEDA_INEXISTENTE, 2.0)));
        edicion.setId(creada.getId());
        edicion.setObservaciones(marca + " EDITADA");

        RuntimeException e = assertThrows(RuntimeException.class, () -> graphQL.actualizarSolicitudPago(edicion));
        assertTrue(e.getMessage().startsWith("Error al actualizar solicitud de pago: Moneda no encontrada"), e.getMessage());

        assertEquals(1, contar("select count(*) from operaciones.solicitud_pago where observaciones = :p", marca));
        assertEquals(1, contar("select count(*) from operaciones.solicitud_pago_detalle where solicitud_pago_id = :p and valor = 1", creada.getId()));
    }
}
