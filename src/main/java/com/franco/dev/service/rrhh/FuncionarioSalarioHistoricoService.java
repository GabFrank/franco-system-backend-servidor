package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioSalarioHistorico;
import com.franco.dev.repository.rrhh.FuncionarioSalarioHistoricoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class FuncionarioSalarioHistoricoService
        extends CrudService<FuncionarioSalarioHistorico, FuncionarioSalarioHistoricoRepository, Long> {

    private final FuncionarioSalarioHistoricoRepository repository;

    @Override
    public FuncionarioSalarioHistoricoRepository getRepository() {
        return repository;
    }

    public List<FuncionarioSalarioHistorico> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaVigenciaDesc(funcionarioId);
    }

    @Override
    public FuncionarioSalarioHistorico save(FuncionarioSalarioHistorico entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());
        return super.save(entity);
    }
}
