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

    List<Sucursal> findAllByOrderByIdAsc();

    @Query("select p from Sucursal p where p.id != 0 and p.activo = ?2 and (?1 is null or ?1 = '' or CAST(p.id as string) like CONCAT('%', ?1, '%') or UPPER(p.nombre) like CONCAT('%', UPPER(?1), '%')) order by p.id asc")
    public List<Sucursal> findByAll(String texto, Boolean activo);

    public List<Sucursal> findByIsConfiguredFalse();

    public Page<Sucursal> findByNombreLike(String nombre, Pageable page);

    public List<Sucursal> findAllByActivoTrueOrderByIdAsc();

//    public List<Pais> findBySubFamiliaId(Long id);

}