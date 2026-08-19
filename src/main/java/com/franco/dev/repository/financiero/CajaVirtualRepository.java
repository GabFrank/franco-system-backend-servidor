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

    // ── Variantes acotadas por el ACL de cajas ──
    //
    // El filtro va en la consulta y no en memoria a proposito: filtrar despues de paginar
    // deja getTotalElements contando cajas que el usuario no puede ver (paginacion mentirosa).
    // Quien ve todas (superusuario, proceso de sistema) usa los metodos de arriba.

    @Query("select c from CajaVirtual c where c.id in :ids")
    Page<CajaVirtual> findAllVisibles(@Param("ids") List<Long> ids, Pageable pageable);

    List<CajaVirtual> findByTipoAndIdIn(CajaVirtualTipo tipo, List<Long> ids);

    List<CajaVirtual> findBySucursalIdAndIdIn(Long sucursalId, List<Long> ids);

    List<CajaVirtual> findByActivoTrueAndIdIn(List<Long> ids);

    /**
     * Filtro combinado (todos opcionales) para la lista de cajas. {@code tipo} llega como
     * String (name del enum) y se compara contra la columna casteada a texto: el enum es un
     * tipo nativo de Postgres y un bind param nulo de enum rompe con 42P18 (no puede inferir
     * el tipo del parámetro); castear a texto lo evita.
     */
    @Query("select c from CajaVirtual c where "
            + "(:nombre is null or lower(c.nombre) like lower(concat('%', :nombre, '%'))) and "
            + "(:tipo is null or cast(c.tipo as string) = :tipo) and "
            + "(:sucursalId is null or c.sucursal.id = :sucursalId) and "
            + "(:activo is null or c.activo = :activo)")
    Page<CajaVirtual> filter(@Param("nombre") String nombre,
                             @Param("tipo") String tipo,
                             @Param("sucursalId") Long sucursalId,
                             @Param("activo") Boolean activo,
                             Pageable pageable);

    /** Igual que {@link #filter}, acotado a las cajas visibles para el usuario. */
    @Query("select c from CajaVirtual c where "
            + "c.id in :ids and "
            + "(:nombre is null or lower(c.nombre) like lower(concat('%', :nombre, '%'))) and "
            + "(:tipo is null or cast(c.tipo as string) = :tipo) and "
            + "(:sucursalId is null or c.sucursal.id = :sucursalId) and "
            + "(:activo is null or c.activo = :activo)")
    Page<CajaVirtual> filterVisibles(@Param("ids") List<Long> ids,
                                     @Param("nombre") String nombre,
                                     @Param("tipo") String tipo,
                                     @Param("sucursalId") Long sucursalId,
                                     @Param("activo") Boolean activo,
                                     Pageable pageable);
}
