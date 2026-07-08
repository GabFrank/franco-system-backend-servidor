package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.HoraExtra;
import com.franco.dev.repository.rrhh.HoraExtraRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class HoraExtraService extends CrudService<HoraExtra, HoraExtraRepository, Long> {

    private final HoraExtraRepository repository;

    @Override
    public HoraExtraRepository getRepository() {
        return repository;
    }

    public List<HoraExtra> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public List<HoraExtra> findByFuncionarioIdAndFechaBetween(Long funcionarioId, LocalDate desde, LocalDate hasta) {
        return repository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(funcionarioId, desde, hasta);
    }

    public List<HoraExtra> findByJornada(Long jornadaId, Long sucursalId) {
        return repository.findByJornadaIdAndSucursalId(jornadaId, sucursalId);
    }

    @Transactional
    public HoraExtra anular(Long id) {
        Optional<HoraExtra> opt = repository.findById(id);
        if (opt.isEmpty()) return null;
        HoraExtra e = opt.get();
        e.setAnulada(true);
        return repository.save(e);
    }

    @Override
    public HoraExtra save(HoraExtra entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getAnulada() == null) entity.setAnulada(false);
        if (entity.getMinutos() == null) entity.setMinutos(BigDecimal.ZERO);
        if (entity.getMontoCalculado() == null) entity.setMontoCalculado(BigDecimal.ZERO);
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }
}
