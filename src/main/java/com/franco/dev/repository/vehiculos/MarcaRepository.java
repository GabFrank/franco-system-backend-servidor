package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.Marca;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MarcaRepository extends HelperRepository<Marca, Long> {

    default Class<Marca> getEntityClass() {
        return Marca.class;
    }

    @Query("select m from Marca m where CAST(m.id as text) like %?1% or UPPER(m.descripcion) like %?1%")
    public List<Marca> findByAll(String texto);

}

