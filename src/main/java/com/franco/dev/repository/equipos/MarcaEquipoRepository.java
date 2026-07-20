package com.franco.dev.repository.equipos;

import com.franco.dev.domain.equipos.MarcaEquipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MarcaEquipoRepository extends HelperRepository<MarcaEquipo, Long> {

    default Class<MarcaEquipo> getEntityClass() {
        return MarcaEquipo.class;
    }

    @Query("select m from MarcaEquipo m where CAST(m.id as text) like %?1% or UPPER(m.descripcion) like %?1%")
    List<MarcaEquipo> findByAll(String texto);
}
