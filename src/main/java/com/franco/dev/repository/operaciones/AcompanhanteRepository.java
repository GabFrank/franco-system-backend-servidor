package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Acompanhante;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcompanhanteRepository extends HelperRepository<Acompanhante, Acompanhante.AcompanhanteId> {

    default Class<Acompanhante> getEntityClass() {
        return Acompanhante.class;
    }

    List<Acompanhante> findByHojaRutaId(Long hojaRutaId);
}
