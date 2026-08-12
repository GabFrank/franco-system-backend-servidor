package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MotivoAveria;
import com.franco.dev.repository.operaciones.MotivoAveriaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MotivoAveriaService extends CrudService<MotivoAveria, MotivoAveriaRepository, Long> {

    private final MotivoAveriaRepository repository;

    @Override
    public MotivoAveriaRepository getRepository() {
        return repository;
    }

    public List<MotivoAveria> findAllActivos() {
        return repository.findAllActivos();
    }
}
