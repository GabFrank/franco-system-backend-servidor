package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.activos.EnteRepository;
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
public class EnteService extends CrudService<Ente, EnteRepository, Long> {

    private final EnteRepository repository;

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

    public Page<Ente> findAllWithFilters(String tipoEnte, Long sucursalId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllWithFilters(tipoEnte, sucursalId, pageable);
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

    public Ente ensureEnteForReferencia(TipoEnte tipoEnte, Long referenciaId, Usuario usuario) {
        if (tipoEnte == null || referenciaId == null) {
            return null;
        }

        Ente ente = repository.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId).orElseGet(Ente::new);
        ente.setTipoEnte(tipoEnte);
        ente.setReferenciaId(referenciaId);
        ente.setActivo(true);
        if (usuario != null) {
            ente.setUsuario(usuario);
        }
        return save(ente);
    }
}
