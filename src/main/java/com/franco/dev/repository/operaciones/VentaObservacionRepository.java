package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.VentaObservacion;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VentaObservacionRepository extends HelperRepository<VentaObservacion, Long> {
    default Class<VentaObservacion> getEntityClass() { return VentaObservacion.class; }

   public List<VentaObservacion> findByVentaIdAndSucursalId(Long ventaId, Long sucursalId);

}
