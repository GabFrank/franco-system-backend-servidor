package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PagoDetalleRepository extends HelperRepository<PagoDetalle, Long> {
    default Class<PagoDetalle> getEntityClass() {
        return PagoDetalle.class;
    }
    
    List<PagoDetalle> findByPagoId(Long pagoId);

    @Query(value = "SELECT sucursal_id FROM operaciones.pago_detalle WHERE id = :pagoDetalleId", nativeQuery = true)
    Long findSucursalIdByPagoDetalleId(@Param("pagoDetalleId") Long pagoDetalleId);
}

