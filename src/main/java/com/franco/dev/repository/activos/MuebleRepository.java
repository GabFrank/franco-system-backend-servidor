package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.Mueble;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MuebleRepository extends HelperRepository<Mueble, Long> {

    default Class<Mueble> getEntityClass() {
        return Mueble.class;
    }

    @Query("select m from Mueble m left join m.propietario p left join m.familia f left join m.tipoMueble tm where " +
            "CAST(m.id as text) like concat('%', ?1, '%') or " +
            "UPPER(m.identificador) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(m.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(f.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(tm.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))")
    List<Mueble> findByAll(String texto);

    @Query("select m from Mueble m left join m.propietario p left join m.familia f left join m.tipoMueble tm where " +
            "(CAST(m.id as text) like concat('%', ?1, '%') or " +
            "UPPER(m.identificador) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(m.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(f.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(tm.descripcion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))) " +
            "order by m.id desc")
    Page<Mueble> findByAllWithPage(String texto, Pageable pageable);

    @Query("select m from Mueble m where m.familia.id = ?1")
    List<Mueble> findByFamiliaId(Long familiaId);

    @Query("select m from Mueble m where m.tipoMueble.id = ?1")
    List<Mueble> findByTipoMuebleId(Long tipoMuebleId);

    List<Mueble> findByPropietarioId(Long propietarioId);
}
