package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.JornadaNovedad;
import com.franco.dev.repository.rrhh.JornadaNovedadRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class JornadaNovedadService extends CrudService<JornadaNovedad, JornadaNovedadRepository, Long> {

    private final JornadaNovedadRepository repository;

    @Override
    public JornadaNovedadRepository getRepository() {
        return repository;
    }

    public List<JornadaNovedad> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public List<JornadaNovedad> findByFuncionarioIdAndFecha(Long funcionarioId, LocalDate fecha) {
        return repository.findByFuncionarioIdAndFecha(funcionarioId, fecha);
    }

    public List<JornadaNovedad> findByFuncionarioIdAndFechaBetween(Long funcionarioId, LocalDate desde, LocalDate hasta) {
        return repository.findByFuncionarioIdAndFechaBetweenOrderByFechaAsc(funcionarioId, desde, hasta);
    }

    public List<JornadaNovedad> findByJornada(Long jornadaId, Long sucursalId) {
        return repository.findByJornadaIdAndSucursalId(jornadaId, sucursalId);
    }

    @Override
    public JornadaNovedad save(JornadaNovedad entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }
}
