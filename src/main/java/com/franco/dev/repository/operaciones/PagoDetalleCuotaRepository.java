package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PagoDetalleCuotaRepository extends HelperRepository<PagoDetalleCuota, Long> {
    default Class<PagoDetalleCuota> getEntityClass() {
        return PagoDetalleCuota.class;
    }
    
    List<PagoDetalleCuota> findByPagoDetalleId(Long pagoDetalleId);
    
    @Query("select c from PagoDetalleCuota c " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(CAST(numeroCuota as text)) like %?1% or UPPER(CAST(referenciaId as text)) like %?1%")
    public List<PagoDetalleCuota> findByAll(String texto);
} 