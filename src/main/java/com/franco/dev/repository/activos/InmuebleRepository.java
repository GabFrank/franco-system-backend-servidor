package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.Inmueble;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InmuebleRepository extends HelperRepository<Inmueble, Long> {

    default Class<Inmueble> getEntityClass() {
        return Inmueble.class;
    }

    @Query("select i from Inmueble i left join i.propietario p where " +
            "CAST(i.id as text) like concat('%', ?1, '%') or " +
            "UPPER(i.nombreAsignado) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(i.direccion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(i.codigoCatastral) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))")
    List<Inmueble> findByAll(String texto);

    @Query("select i from Inmueble i left join i.propietario p where " +
            "(CAST(i.id as text) like concat('%', ?1, '%') or " +
            "UPPER(i.nombreAsignado) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(i.direccion) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(i.codigoCatastral) like UPPER(concat('%', ?1, '%')) or " +
            "UPPER(p.nombre) like UPPER(concat('%', ?1, '%'))) " +
            "order by i.id desc")
    Page<Inmueble> findByAllWithPage(String texto, Pageable pageable);

    List<Inmueble> findByPropietarioId(Long propietarioId);

    @Query("select i from Inmueble i where i.pais.id = ?1")
    List<Inmueble> findByPaisId(Long paisId);

    @Query("select i from Inmueble i where i.ciudad.id = ?1")
    List<Inmueble> findByCiudadId(Long ciudadId);
}
