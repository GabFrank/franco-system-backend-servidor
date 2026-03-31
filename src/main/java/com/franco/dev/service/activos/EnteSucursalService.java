package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.EnteSucursal;
import com.franco.dev.repository.activos.EnteSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class EnteSucursalService extends CrudService<EnteSucursal, EnteSucursalRepository, Long> {

    private final EnteSucursalRepository repository;

    @Override
    public EnteSucursalRepository getRepository() {
        return repository;
    }

    public List<EnteSucursal> findByEnteId(Long enteId) {
        return repository.findByEnteId(enteId);
    }

    public List<EnteSucursal> findBySucursalId(Long sucursalId) {
        return repository.findBySucursalId(sucursalId);
    }

    public Optional<EnteSucursal> findByEnteIdAndSucursalId(Long enteId, Long sucursalId) {
        return repository.findByEnteIdAndSucursalId(enteId, sucursalId);
    }

    public Page<EnteSucursal> findAllWithFilters(String texto, Long sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllWithFilters(texto, sucursalId, pageable);
    }

    @Override
    public EnteSucursal save(EnteSucursal entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
