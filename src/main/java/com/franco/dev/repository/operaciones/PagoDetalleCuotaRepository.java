package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.operaciones.enums.PagoDetalleCuotaEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PagoDetalleCuotaRepository extends HelperRepository<PagoDetalleCuota, Long>, JpaSpecificationExecutor<PagoDetalleCuota> {
    default Class<PagoDetalleCuota> getEntityClass() {
        return PagoDetalleCuota.class;
    }
    
    List<PagoDetalleCuota> findByPagoDetalleId(Long pagoDetalleId);
    
    @Query("select c from PagoDetalleCuota c " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(CAST(numeroCuota as text)) like %?1% or UPPER(CAST(referenciaId as text)) like %?1%")
    public List<PagoDetalleCuota> findByAll(String texto);
    
    @Query(value = "SELECT pdc.id FROM operaciones.pago_detalle_cuota pdc " +
            "JOIN operaciones.pago_detalle pd ON pdc.pago_detalle_id = pd.id " +
            "WHERE pd.sucursal_id = :sucursalId", 
            nativeQuery = true)
    List<Long> findIdsBySucursalId(@Param("sucursalId") Long sucursalId);
} 