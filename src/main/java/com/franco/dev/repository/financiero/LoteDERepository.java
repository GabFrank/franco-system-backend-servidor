package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.repository.HelperRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LoteDERepository extends HelperRepository<LoteDE, Long> {

    default Class<LoteDE> getEntityClass() {
        return LoteDE.class;
    }

    List<LoteDE> findAllByOrderByIdAsc(Pageable pageable);

    @Query("SELECT l FROM LoteDE l WHERE " +
           "(l.estado = :estado OR cast(:estado as com.franco.dev.domain.financiero.enums.EstadoLoteDE) IS NULL) AND " +
           "(l.fechaProcesado >= :fechaInicio OR cast(:fechaInicio as timestamp) IS NULL) AND " +
           "(l.fechaProcesado <= :fechaFin OR cast(:fechaFin as timestamp) IS NULL) " +
           "ORDER BY l.id ASC")
    Page<LoteDE> findByEstadoOrFechaProcesadoBetween(
        @Param("estado") EstadoLoteDE estado,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );
    
}


 