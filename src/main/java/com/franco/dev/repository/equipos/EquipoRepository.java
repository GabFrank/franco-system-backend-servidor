package com.franco.dev.repository.equipos;

import com.franco.dev.domain.equipos.Equipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EquipoRepository extends HelperRepository<Equipo, Long> {

    default Class<Equipo> getEntityClass() {
        return Equipo.class;
    }

    @Query("select e from Equipo e left join e.propietario p left join e.tipoEquipo te left join e.modelo mo left join mo.marca ma where " +
            "CAST(e.id as text) like concat('%', ?1, '%') or " +
            "UPPER(e.identificador) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(e.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(mo.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(ma.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(te.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))")
    List<Equipo> findByAll(String texto);

    @Query("select e from Equipo e left join e.propietario p left join e.tipoEquipo te left join e.modelo mo left join mo.marca ma where " +
            "(CAST(e.id as text) like concat('%', ?1, '%') or " +
            "UPPER(e.identificador) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(e.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(mo.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(ma.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(te.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))) " +
            "order by e.id desc")
    Page<Equipo> findByAllWithPage(String texto, Pageable pageable);

    List<Equipo> findByTipoEquipoId(Long tipoEquipoId);

    List<Equipo> findByPropietarioId(Long propietarioId);

    List<Equipo> findByModeloId(Long modeloId);
}
