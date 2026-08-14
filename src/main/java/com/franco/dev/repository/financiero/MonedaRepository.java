package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MonedaRepository extends HelperRepository<Moneda, Long> {

    default Class<Moneda> getEntityClass() {
        return Moneda.class;
    }

    @Query("select m from Moneda m " +
            "where UPPER(CAST(m.id as text)) like UPPER(concat('%', ?1, '%')) or UPPER(m.denominacion) like UPPER(concat('%', ?1, '%'))")
    public List<Moneda> findByAll(String texto);

    public Moneda findByDenominacion(String texto);

    /** Primera moneda cuya denominación contiene el texto (case-insensitive). Usado como fallback (ej. Guaraníes). */
    Moneda findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc(String texto);

    List<Moneda> findAllByOrderByIdAsc();

    //    Moneda findByPaisId(Long id);

}