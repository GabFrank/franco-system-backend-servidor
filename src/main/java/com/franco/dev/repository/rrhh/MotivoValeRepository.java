package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.MotivoVale;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MotivoValeRepository extends HelperRepository<MotivoVale, Long> {

    default Class<MotivoVale> getEntityClass() {
        return MotivoVale.class;
    }

    List<MotivoVale> findByActivoTrueOrderByNombreAsc();

    /**
     * Padron del SaaS: toda lista paginada y filtrada en el backend.
     * El texto busca en nombre Y descripcion: la lista muestra el nombre, asi que
     * filtrar solo por descripcion hacia que buscar lo que se ve en pantalla no
     * encontrara nada (ej. "QUINCENA" vive en nombre).
     */
    @Query("select m from MotivoVale m where " +
            "(:descripcion is null or upper(m.nombre) like concat('%', upper(:descripcion), '%') " +
            "   or upper(m.descripcion) like concat('%', upper(:descripcion), '%')) and " +
            "(cast(:activo as boolean) is null or m.activo = :activo) " +
            "order by m.nombre asc, m.id asc")
    Page<MotivoVale> findPage(@Param("descripcion") String descripcion,
                              @Param("activo") Boolean activo,
                              Pageable pageable);
}
