package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CargoRepository extends HelperRepository<Cargo, Long> {

    default Class<Cargo> getEntityClass() {
        return Cargo.class;
    }

    public List<Cargo> findByDescripcion(String texto);

    /** Cuantos cargos dependen jerarquicamente de este. Bloquea su borrado. */
    Long countBySupervisadoPorId(Long cargoId);

    @Query("select p from Cargo p where CAST(id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.descripcion) like %?1%")
    public List<Cargo> findByAll(String texto);

    /** Lista de cargos del ABM: mismo filtro que findByAll, paginado, el ultimo creado primero. */
    @Query(value = "select p from Cargo p where CAST(p.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.descripcion) like %?1% order by p.id desc",
            countQuery = "select count(p) from Cargo p where CAST(p.id as text) like %?1% or UPPER(p.nombre) like %?1% or UPPER(p.descripcion) like %?1%")
    Page<Cargo> findByAllWithPage(String texto, Pageable pageable);

//    public List<Pais> findBySubFamiliaId(Long id);

}