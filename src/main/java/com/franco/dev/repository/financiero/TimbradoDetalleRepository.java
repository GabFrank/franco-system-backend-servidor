package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface TimbradoDetalleRepository extends HelperRepository<TimbradoDetalle, Long> {

    default Class<TimbradoDetalle> getEntityClass() {
        return TimbradoDetalle.class;
    }
    public List<TimbradoDetalle> findByTimbradoId(Long id);
    
    @Query(value = "SELECT * FROM financiero.timbrado_detalle td WHERE td.timbrado_id = ?1 ORDER BY td.id ASC", nativeQuery = true)
    public Page<TimbradoDetalle> findByTimbradoId(Long timbradoId, Pageable pageable);
}