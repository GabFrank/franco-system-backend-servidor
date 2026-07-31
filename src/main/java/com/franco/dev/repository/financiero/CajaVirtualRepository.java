package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CajaVirtualRepository extends JpaRepository<CajaVirtual, Long> {

    List<CajaVirtual> findByTipo(CajaVirtualTipo tipo);

    List<CajaVirtual> findBySucursalId(Long sucursalId);

    List<CajaVirtual> findByActivoTrue();

    Page<CajaVirtual> findAll(Pageable pageable);

    /** Filtro combinado (todos opcionales) para la lista de cajas. */
    @Query("select c from CajaVirtual c where "
            + "(:nombre is null or lower(c.nombre) like lower(concat('%', :nombre, '%'))) and "
            + "(:tipo is null or c.tipo = :tipo) and "
            + "(:sucursalId is null or c.sucursal.id = :sucursalId) and "
            + "(:activo is null or c.activo = :activo)")
    Page<CajaVirtual> filter(@Param("nombre") String nombre,
                             @Param("tipo") CajaVirtualTipo tipo,
                             @Param("sucursalId") Long sucursalId,
                             @Param("activo") Boolean activo,
                             Pageable pageable);
}
