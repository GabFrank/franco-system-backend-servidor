package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaObservacion;
import com.franco.dev.repository.financiero.CajaObservacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CajaObservacionService extends CrudService<CajaObservacion, CajaObservacionRepository, Long> {

    private final CajaObservacionRepository repository;

    @Override
    public CajaObservacionRepository getRepository() { return repository; }

    public List<CajaObservacion> findByPdvCajaIdAndSucursalId(Long cajaId, Long sucursalId) {
        return repository.findByPdvCajaIdAndSucursalId(cajaId, sucursalId);
    }

    public CajaObservacion save(CajaObservacion entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        CajaObservacion e = super.save(entity);
        return e;
    }
}
