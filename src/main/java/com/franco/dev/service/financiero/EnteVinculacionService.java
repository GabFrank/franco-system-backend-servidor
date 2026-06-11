package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EnteVinculacion;
import com.franco.dev.repository.financiero.EnteVinculacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class EnteVinculacionService extends CrudService<EnteVinculacion, EnteVinculacionRepository, Long> {

    private final EnteVinculacionRepository repository;

    @Override
    public EnteVinculacionRepository getRepository() {
        return repository;
    }

    public List<EnteVinculacion> findByEnteId(Long enteId) {
        return repository.findByEnteId(enteId);
    }

    public List<EnteVinculacion> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public Page<EnteVinculacion> findAllByEnteIdAndSucursalId(Long enteId, Long sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllByEnteIdAndSucursalId(enteId, sucursalId, pageable);
    }

    @Override
    public EnteVinculacion save(EnteVinculacion entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
