package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaletinRepository extends HelperRepository<Maletin, Long> {

    default Class<Maletin> getEntityClass() {
        return Maletin.class;
    }

    @Query("select m from Maletin m " +
            "join m.sucursal s " +
            "where (UPPER(CAST(m.id as text)) like %?1% or UPPER(m.descripcion) like %?1%)")
    public List<Maletin> findByAllWithoutSucursal(String texto);

    @Query("select m from Maletin m " +
            "join m.sucursal s " +
            "where s.id = ?2 and (UPPER(CAST(m.id as text)) like %?1% or UPPER(m.descripcion) like %?1%)")
    public List<Maletin> findByAllWithSucursal(String texto, Long sucId);

    // Lista de la pantalla de maletines: filtros opcionales (null = sin filtrar), abierto null cuenta como cerrado.
    @Query(value = "select m from Maletin m left join m.sucursal s where " +
            "(:sucursalId is null or s.id = :sucursalId) and " +
            "(:texto is null or upper(m.descripcion) like upper(concat('%', :texto, '%'))) and " +
            "(:abierto is null or coalesce(m.abierto, false) = :abierto) " +
            "order by m.id asc",
            countQuery = "select count(m) from Maletin m left join m.sucursal s where " +
            "(:sucursalId is null or s.id = :sucursalId) and " +
            "(:texto is null or upper(m.descripcion) like upper(concat('%', :texto, '%'))) and " +
            "(:abierto is null or coalesce(m.abierto, false) = :abierto)")
    Page<Maletin> findConFiltros(@Param("sucursalId") Long sucursalId, @Param("texto") String texto,
                                 @Param("abierto") Boolean abierto, Pageable pageable);

    Maletin findByDescripcionIgnoreCase(String descripcion);

    Page<Maletin> findAll(Pageable pageable);

    List<Maletin> findBySucursalId(Long id);
}