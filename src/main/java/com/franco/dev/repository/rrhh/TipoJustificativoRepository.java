package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.TipoJustificativo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TipoJustificativoRepository extends HelperRepository<TipoJustificativo, Long> {

    default Class<TipoJustificativo> getEntityClass() {
        return TipoJustificativo.class;
    }

    List<TipoJustificativo> findByActivoTrueOrderByNombreAsc();

    TipoJustificativo findFirstByNombreIgnoreCase(String nombre);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select t from TipoJustificativo t where " +
            "(:nombre is null or upper(t.nombre) like concat('%', upper(:nombre), '%')) and " +
            "(cast(:activo as boolean) is null or t.activo = :activo) " +
            "order by t.nombre asc, t.id asc")
    Page<TipoJustificativo> findPage(@Param("nombre") String nombre,
                                     @Param("activo") Boolean activo,
                                     Pageable pageable);
}
