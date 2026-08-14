package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.repository.rrhh.JustificativoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class JustificativoService extends CrudService<Justificativo, JustificativoRepository, Long> {

    private final JustificativoRepository repository;

    @Override
    public JustificativoRepository getRepository() {
        return repository;
    }

    public List<Justificativo> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public List<Justificativo> findByFuncionarioIdAndFecha(Long funcionarioId, LocalDate fecha) {
        return repository.findByFuncionarioIdAndFecha(funcionarioId, fecha);
    }

    public List<Justificativo> findByFuncionarioIdAndFechaBetween(Long funcionarioId, LocalDate desde, LocalDate hasta) {
        return repository.findByFuncionarioIdAndFechaBetweenOrderByFechaAsc(funcionarioId, desde, hasta);
    }

    public List<Justificativo> findByJornada(Long jornadaId, Long sucursalId) {
        return repository.findByJornadaIdAndSucursalId(jornadaId, sucursalId);
    }

    public Page<Justificativo> findPage(Long funcionarioId, Long tipoId, LocalDate desde, LocalDate hasta,
                                        Pageable pageable) {
        return repository.findPage(funcionarioId, tipoId, desde, hasta, pageable);
    }

    @Override
    public Justificativo save(Justificativo entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }
}
