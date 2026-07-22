package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.service.CrudService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class BonoService extends CrudService<Bono, BonoRepository, Long> {

    private final BonoRepository repository;

    @Override
    public BonoRepository getRepository() {
        return repository;
    }

    public List<Bono> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public Page<Bono> findPage(Long funcionarioId, BonoTipo tipo, LocalDate desde, LocalDate hasta,
                               Pageable pageable) {
        return repository.findPage(funcionarioId, tipo, desde, hasta, pageable);
    }

    @Transactional
    public Bono anular(Long id) {
        Optional<Bono> opt = repository.findById(id);
        if (opt.isEmpty()) throw new GraphQLException("Bono no encontrado");
        Bono b = opt.get();
        b.setAnulado(true);
        return repository.save(b);
    }

    @Override
    public Bono save(Bono entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getAnulado() == null) entity.setAnulado(false);
        if (entity.getEsRecurrente() == null) entity.setEsRecurrente(false);
        if (entity.getMonto() == null) entity.setMonto(BigDecimal.ZERO);
        if (entity.getMotivo() != null) entity.setMotivo(entity.getMotivo().toUpperCase());
        return super.save(entity);
    }
}
