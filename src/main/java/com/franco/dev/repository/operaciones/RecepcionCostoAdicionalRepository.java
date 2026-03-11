package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RecepcionCostoAdicional;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecepcionCostoAdicionalRepository extends HelperRepository<RecepcionCostoAdicional, Long> {
    
    default Class<RecepcionCostoAdicional> getEntityClass() {
        return RecepcionCostoAdicional.class;
    }

    @Query("SELECT rca FROM RecepcionCostoAdicional rca WHERE rca.recepcionMercaderia.id = :recepcionId")
    List<RecepcionCostoAdicional> findByRecepcionMercaderiaId(@Param("recepcionId") Long recepcionId);
} 