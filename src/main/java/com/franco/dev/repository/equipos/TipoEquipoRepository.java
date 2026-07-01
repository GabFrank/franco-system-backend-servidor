package com.franco.dev.repository.equipos;

import com.franco.dev.domain.equipos.TipoEquipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoEquipoRepository extends HelperRepository<TipoEquipo, Long> {

    default Class<TipoEquipo> getEntityClass() {
        return TipoEquipo.class;
    }

    @Query("select t from TipoEquipo t where " +
            "CAST(t.id as text) like concat('%', ?1, '%') or " +
            "UPPER(t.descripcion) like UPPER(concat('%', ?1, '%')) " +
            "order by t.id desc")
    List<TipoEquipo> findByAll(String texto);

    @Query("select t from TipoEquipo t where " +
            "CAST(t.id as text) like concat('%', ?1, '%') or " +
            "UPPER(t.descripcion) like UPPER(concat('%', ?1, '%')) " +
            "order by t.id desc")
    Page<TipoEquipo> findByAllWithPage(String texto, Pageable pageable);
}
