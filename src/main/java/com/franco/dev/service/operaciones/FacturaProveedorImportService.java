package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import com.franco.dev.repository.operaciones.FacturaProveedorImportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FacturaProveedorImportService {

    @Autowired
    private FacturaProveedorImportRepository repository;

    public FacturaProveedorImport save(FacturaProveedorImport e) {
        return repository.save(e);
    }

    public Optional<FacturaProveedorImport> findById(Long id) {
        return repository.findById(id);
    }

    public Page<FacturaProveedorImport> findPaged(EstadoImport estado, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (estado == null) {
            return repository.findAllByOrderByCreadoEnDesc(pageable);
        }
        return repository.findByEstadoOrderByCreadoEnDesc(estado, pageable);
    }
}
