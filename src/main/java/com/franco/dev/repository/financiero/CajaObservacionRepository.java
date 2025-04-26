package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaObservacion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface CajaObservacionRepository extends HelperRepository<CajaObservacion, Long> {
    default Class<CajaObservacion> getEntityClass() { return CajaObservacion.class; }

    public List<CajaObservacion> findByPdvCajaIdAndSucursalId(Long cajaId, Long sucursalId);
}
