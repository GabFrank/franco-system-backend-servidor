package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CambioRepository extends HelperRepository<Cambio, Long> {

    default Class<Cambio> getEntityClass() {
        return Cambio.class;
    }

    @Query(value = "select * from financiero.cambio c " +
            "where c.moneda_id = ?1 order by creado_en desc limit 1", nativeQuery = true)
    public Cambio findLastByCambioId(Long id);

    @Query("select e from Cambio e " +
            " where cast(e.creadoEn as date) = cast(?1 as date) and ?2 = ?1" +
            "or cast(e.creadoEn as date) >= cast(?1 as date) AND cast(e.creadoEn as date) <= cast(?2 as date)")
    public List<Cambio> findByDate(String start, String end);

}