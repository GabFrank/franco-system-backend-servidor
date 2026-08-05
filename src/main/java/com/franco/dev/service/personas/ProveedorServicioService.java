package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.repository.personas.ProveedorServicioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ProveedorServicioService extends CrudService<ProveedorServicio, ProveedorServicioRepository, Long> {

    private final ProveedorServicioRepository repository;

    @Override
    public ProveedorServicioRepository getRepository() {
        return repository;
    }

    public ProveedorServicio findByPersonaId(Long id) {
        return repository.findByPersonaId(id);
    }

    /**
     * @CreationTimestamp solo cubre el insert; el save defensivo evita quedarse con
     * creado_en nulo si la fila viene de una edicion previa a esta columna.
     */
    @Override
    public ProveedorServicio save(ProveedorServicio entity) {
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        return super.save(entity);
    }
}
