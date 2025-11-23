package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class InicioSesionService extends CrudService<InicioSesion, InicioSesionRepository, Long> {

    private final InicioSesionRepository repository;

    @Override
    public InicioSesionRepository getRepository() {
        return repository;
    }

    public List<InicioSesion> findAll() {
        return repository.findAll();
    }

    @Override
    public InicioSesion save(InicioSesion entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        InicioSesion e = super.save(entity);
        return e;
    }

    public Page<InicioSesion> findByUsuarioIdAndHoraFinIsNul(Long id, Long sucId, Pageable pageable) {
        if (sucId != null) {
            return repository.findByUsuarioIdAndSucursalIdAndHoraFinIsNullOrderByIdDesc(id, sucId, pageable);
        } else {
            return repository.findByUsuarioIdAndHoraFinIsNullOrderByIdDesc(id, pageable);
        }
    }

    @Override
    public InicioSesion saveAndSend(InicioSesion entity, Boolean recibir) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }

        InicioSesion e = super.save(entity);
        return e;
    }

    public List<InicioSesion> findActiveSessionsByUsuarioIds(Collection<Long> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByUsuarioIdInAndHoraFinIsNullOrderByIdDesc(usuarioIds);
    }

    @org.springframework.transaction.annotation.Transactional
    public void clearToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        repository.clearTokenByToken(token);
    }
}