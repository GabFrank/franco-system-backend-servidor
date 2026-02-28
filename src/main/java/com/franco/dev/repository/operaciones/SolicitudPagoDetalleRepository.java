package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPagoDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SolicitudPagoDetalleRepository extends HelperRepository<SolicitudPagoDetalle, Long>, JpaSpecificationExecutor<SolicitudPagoDetalle> {

    default Class<SolicitudPagoDetalle> getEntityClass() {
        return SolicitudPagoDetalle.class;
    }

    List<SolicitudPagoDetalle> findBySolicitudPagoIdOrderByOrdenAscIdAsc(Long solicitudPagoId);

    /**
     * Same as findBySolicitudPagoIdOrderByOrdenAscIdAsc but with moneda and formaPago eagerly loaded.
     * Use for printing (PDF/ticket) to avoid LazyInitializationException when accessing moneda/formaPago.
     */
    @Query("SELECT d FROM SolicitudPagoDetalle d " +
           "LEFT JOIN FETCH d.moneda " +
           "LEFT JOIN FETCH d.formaPago " +
           "WHERE d.solicitudPago.id = :solicitudPagoId " +
           "ORDER BY d.orden ASC, d.id ASC")
    List<SolicitudPagoDetalle> findBySolicitudPagoIdOrderByOrdenAscIdAscWithMonedaAndFormaPago(@Param("solicitudPagoId") Long solicitudPagoId);

    void deleteBySolicitudPagoId(Long solicitudPagoId);
}
