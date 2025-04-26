package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Repository
public class VentaCreditoRepositoryImpl {

    @PersistenceContext
    private EntityManager entityManager;

    public List<VentaCredito> findVentaCreditoWithFilters(@Param("clienteId") Long clienteId,
                                                          @Param("estado") EstadoVentaCredito estado, int offset, int limit) {
        return entityManager.createQuery("SELECT vc FROM VentaCredito vc " +
                        "WHERE vc.cliente.id = (:clienteId) " +
                        "AND (vc.estado = (:estado) or cast(:estado as com.franco.dev.domain.financiero.enums.EstadoVentaCredito) IS NULL) " +
                        "ORDER BY vc.creadoEn DESC",
                VentaCredito.class)
                .setParameter("clienteId", clienteId)
                .setParameter("estado", estado)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
