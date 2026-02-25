package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

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

    Maletin findByDescripcionIgnoreCase(String descripcion);

    Page<Maletin> findAll(Pageable pageable);

    List<Maletin> findBySucursalId(Long id);
}