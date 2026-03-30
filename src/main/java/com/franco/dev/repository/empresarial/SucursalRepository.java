package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SucursalRepository extends HelperRepository<Sucursal, Long> {

    default Class<Sucursal> getEntityClass() {
        return Sucursal.class;
    }

    // Por defecto solo retorna sucursales activas
    List<Sucursal> findAllByActivoTrueOrderByIdAsc();

    // Método original modificado para incluir filtro de activo
    List<Sucursal> findAllByOrderByIdAsc();

    @Query("select p from Sucursal p where p.id != 0 and p.activo = true and (cast(?1 as text) is null or (CAST(id as text) like %?1% or UPPER(p.nombre) like %?1%)) order by p.id asc")
    public List<Sucursal> findByAll(String texto);

    // Método original modificado para incluir filtro de activo
    @Query("select p from Sucursal p where p.id != 0 and p.activo = ?2 and (?1 is null or ?1 = '' or CAST(p.id as string) like CONCAT('%', ?1, '%') or UPPER(p.nombre) like CONCAT('%', UPPER(?1), '%')) order by p.id asc")
    public List<Sucursal> findByAllWithActivoFilter(String texto, Boolean activo);

    @Query("select p from Sucursal p where p.id != 0 and p.activo = ?2 and (?1 is null or ?1 = '' or CAST(p.id as string) like CONCAT('%', ?1, '%') or UPPER(p.nombre) like CONCAT('%', UPPER(?1), '%')) order by p.id asc")
    public Page<Sucursal> findByAllWithActivoFilterPage(String texto, Boolean activo, Pageable page);

    public List<Sucursal> findByIsConfiguredFalseAndActivoTrue();

    // Método original modificado para incluir filtro de activo
    public List<Sucursal> findByIsConfiguredFalse();

    @Query("select s from Sucursal s where s.id != 0 and UPPER(s.nombre) like UPPER(?1) order by s.id asc")
    public Page<Sucursal> findByNombreLikeAndActivo(String nombre, Boolean activo, Pageable page);

    // Método original modificado para incluir filtro de activo
    public Page<Sucursal> findByNombreLike(String nombre, Pageable page);

    // Método con filtros completos
    @Query("SELECT s FROM Sucursal s WHERE " +
           "(:nombre IS NULL OR UPPER(s.nombre) LIKE UPPER(CONCAT('%', :nombre, '%'))) AND " +
           "(:deposito IS NULL OR s.deposito = :deposito) AND " +
           "(:activo IS NULL OR s.activo = :activo) " +
           "ORDER BY s.id ASC")
    public Page<Sucursal> findByNombreConFiltros(String nombre, Boolean deposito, Boolean activo, Pageable pageable);

}