package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioCargoHistorico;
import com.franco.dev.repository.rrhh.FuncionarioCargoHistoricoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FuncionarioCargoHistoricoService
        extends CrudService<FuncionarioCargoHistorico, FuncionarioCargoHistoricoRepository, Long> {

    private final FuncionarioCargoHistoricoRepository repository;

    @Override
    public FuncionarioCargoHistoricoRepository getRepository() {
        return repository;
    }

    public List<FuncionarioCargoHistorico> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesdeDesc(funcionarioId);
    }

    public List<FuncionarioCargoHistorico> findVigentes(Long funcionarioId) {
        return repository.findByFuncionarioIdAndFechaHastaIsNull(funcionarioId);
    }

    @Override
    public FuncionarioCargoHistorico save(FuncionarioCargoHistorico entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());
        return super.save(entity);
    }
}
