package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.VentaObservacion;
import com.franco.dev.repository.operaciones.VentaObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaObservacionService extends CrudService<VentaObservacion, VentaObservacionRepository, Long> {

    private final VentaObservacionRepository repository;

    @Override
    public VentaObservacionRepository getRepository() { return repository; }

    public List<VentaObservacion> findByVentaIdAndSucursalId(Long ventaId, Long sucursalId) {
        return repository.findByVentaIdAndSucursalId(ventaId, sucursalId);
    }

    public VentaObservacion save(VentaObservacion entity) {
        if(entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        VentaObservacion e = super.save(entity);
        return e;
    }
}
