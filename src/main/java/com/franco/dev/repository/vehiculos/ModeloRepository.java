package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.Modelo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ModeloRepository extends HelperRepository<Modelo, Long> {

    default Class<Modelo> getEntityClass() {
        return Modelo.class;
    }

    @Query("select m from Modelo m where CAST(m.id as text) like %?1% or UPPER(m.descripcion) like %?1%")
    public List<Modelo> findByAll(String texto);

    @Query("select m from Modelo m where CAST(m.id as text) like %?1% or UPPER(m.descripcion) like %?1%")
    public Page<Modelo> findByAllWithPage(String texto, Pageable pageable);

    @Query("select m from Modelo m where m.marca.id = ?1 and (CAST(m.id as text) like %?2% or UPPER(m.descripcion) like %?2%)")
    public Page<Modelo> findByMarcaIdAndTexto(Long marcaId, String texto, Pageable pageable);

    public List<Modelo> findByMarcaId(Long marcaId);

}

