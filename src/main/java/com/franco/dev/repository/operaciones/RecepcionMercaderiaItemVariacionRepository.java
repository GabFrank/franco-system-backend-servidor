package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecepcionMercaderiaItemVariacionRepository extends HelperRepository<RecepcionMercaderiaItemVariacion, Long> {

    @Query("select e from RecepcionMercaderiaItemVariacion e where e.recepcionMercaderiaItem.id = ?1")
    List<RecepcionMercaderiaItemVariacion> findByRecepcionMercaderiaItemId(Long id);

    void deleteByRecepcionMercaderiaItemId(Long id);

}
