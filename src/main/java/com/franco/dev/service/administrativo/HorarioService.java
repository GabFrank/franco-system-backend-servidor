package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.repository.administrativo.HorarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class HorarioService extends CrudService<Horario, HorarioRepository, Long> {

    private final HorarioRepository repository;

    @Override
    public HorarioRepository getRepository() {
        return repository;
    }
}
