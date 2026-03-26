package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.repository.financiero.EnteCuotaRepository;
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
public class EnteCuotaService extends CrudService<EnteCuota, EnteCuotaRepository, Long> {

    private final EnteCuotaRepository repository;

    @Override
    public EnteCuotaRepository getRepository() {
        return repository;
    }

    public List<EnteCuota> findByEnteFinancieroId(Long enteFinancieroId) {
        return repository.findByEnteFinancieroId(enteFinancieroId);
    }

    public List<EnteCuota> findPendientesByEnteFinancieroId(Long enteFinancieroId) {
        return repository.findPendientesByEnteFinancieroId(enteFinancieroId);
    }

    public Page<EnteCuota> findAllByEnteFinancieroId(Long enteFinancieroId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllByEnteFinancieroId(enteFinancieroId, pageable);
    }

    @Override
    public EnteCuota save(EnteCuota entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getPagado() == null) {
            entity.setPagado(false);
        }
        return super.save(entity);
    }
}
