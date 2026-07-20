package com.franco.dev.repository.equipos;

import com.franco.dev.domain.equipos.ModeloEquipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModeloEquipoRepository extends HelperRepository<ModeloEquipo, Long> {

    default Class<ModeloEquipo> getEntityClass() {
        return ModeloEquipo.class;
    }

    @Query("select m from ModeloEquipo m left join m.marca ma where " +
            "CAST(m.id as text) like %?1% or UPPER(m.descripcion) like %?1% or UPPER(ma.descripcion) like %?1%")
    List<ModeloEquipo> findByAll(String texto);

    @Query("select m from ModeloEquipo m left join m.marca ma where " +
            "CAST(m.id as text) like %?1% or UPPER(m.descripcion) like %?1% or UPPER(ma.descripcion) like %?1% " +
            "order by m.id desc")
    Page<ModeloEquipo> findByAllWithPage(String texto, Pageable pageable);

    @Query("select m from ModeloEquipo m where m.marca.id = ?1 and " +
            "(CAST(m.id as text) like %?2% or UPPER(m.descripcion) like %?2%)")
    Page<ModeloEquipo> findByMarcaIdAndTexto(Long marcaId, String texto, Pageable pageable);

    List<ModeloEquipo> findByMarcaId(Long marcaId);
}
