package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.activos.EnteRepository;
import com.franco.dev.service.CrudService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class EnteService extends CrudService<Ente, EnteRepository, Long> {

    private final EnteRepository repository;

    public EnteService(EnteRepository repository) {
        this.repository = repository;
    }

    @Override
    public EnteRepository getRepository() {
        return repository;
    }

    public List<Ente> findByTipoEnte(TipoEnte tipoEnte) {
        return repository.findByTipoEnte(tipoEnte);
    }

    public Optional<Ente> findByTipoEnteAndReferenciaId(TipoEnte tipoEnte, Long referenciaId) {
        return repository.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId);
    }

    public List<Ente> findAllActivos() {
        return repository.findAllActivos();
    }

    @Override
    public Ente save(Ente entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        if (entity.getActivo() == null) {
            entity.setActivo(true);
        }
        return super.save(entity);
    }

    public Ente ensureEnteForReferencia(TipoEnte tipoEnte, Long referenciaId, String descripcion, Usuario usuario) {
        if (tipoEnte == null || referenciaId == null) {
            return null;
        }

        Ente ente = repository.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId).orElseGet(Ente::new);
        ente.setTipoEnte(tipoEnte);
        ente.setReferenciaId(referenciaId);
        ente.setDescripcion(descripcion != null ? descripcion.toUpperCase() : null);
        ente.setActivo(true);
        if (usuario != null) {
            ente.setUsuario(usuario);
        }
        return save(ente);
    }
}
