package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.EnteArchivo;
import com.franco.dev.repository.activos.EnteArchivoRepository;
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
public class EnteArchivoService extends CrudService<EnteArchivo, EnteArchivoRepository, Long> {

    private final EnteArchivoRepository repository;

    @Override
    public EnteArchivoRepository getRepository() {
        return repository;
    }

    public List<EnteArchivo> findByEnteId(Long enteId) {
        return repository.findByEnteId(enteId);
    }

    public List<EnteArchivo> findVigentesByEnteId(Long enteId) {
        return repository.findByEnteIdAndVigenteTrue(enteId);
    }

    public Page<EnteArchivo> findAllByEnteId(Long enteId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllByEnteId(enteId, pageable);
    }

    @Override
    public EnteArchivo save(EnteArchivo entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            // Si es un nuevo archivo, marcar los anteriores del mismo tipo como no vigentes
            if(entity.getEnte() != null && entity.getTipoArchivo() != null) {
                repository.desactivarAnteriores(entity.getEnte().getId(), entity.getTipoArchivo());
            }
        }
        if (entity.getVigente() == null) {
            entity.setVigente(true);
        }
        return super.save(entity);
    }
}
