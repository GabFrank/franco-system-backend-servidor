package com.franco.dev.service.rrhh;

import com.franco.dev.service.rrhh.builder.AguinaldoCalculator;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.domain.rrhh.enums.AguinaldoEstado;
import com.franco.dev.repository.rrhh.AguinaldoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.FuncionarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AguinaldoService extends CrudService<Aguinaldo, AguinaldoRepository, Long> {

    private final AguinaldoRepository repository;
    private final FuncionarioService funcionarioService;

    @Override
    public AguinaldoRepository getRepository() {
        return repository;
    }

    public List<Aguinaldo> findByAnio(Integer anio) {
        return repository.findByAnioOrderByIdAsc(anio);
    }

    public List<Aguinaldo> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByAnioDesc(funcionarioId);
    }

    /**
     * Calcula el aguinaldo del anio para todos los funcionarios activos.
     * aguinaldo = (sueldo x meses_trabajados) / 12. Idempotente por
     * (funcionario, anio): si ya existe CALCULADO lo actualiza; no toca los
     * APROBADO/PAGADO. Devuelve la cantidad procesada.
     */
    @Transactional
    public int calcularAguinaldosAnio(Integer anio) {
        int n = 0;
        for (Funcionario f : funcionarioService.findAll2()) {
            if (!Boolean.TRUE.equals(f.getActivo())) continue;
            if (f.getSueldo() == null || f.getFechaIngreso() == null) continue;

            if (f.getFechaIngreso().getYear() > anio) {
                continue;
            }
            int mesesTrabajados = AguinaldoCalculator.mesesTrabajados(anio, f.getFechaIngreso());

            BigDecimal sueldo = new BigDecimal(f.getSueldo().toString());
            BigDecimal monto = AguinaldoCalculator.calcularMonto(sueldo, mesesTrabajados);

            Optional<Aguinaldo> existente = repository.findByFuncionarioIdAndAnio(f.getId(), anio);
            Aguinaldo a = existente.orElseGet(Aguinaldo::new);
            if (a.getEstado() == AguinaldoEstado.APROBADO || a.getEstado() == AguinaldoEstado.PAGADO) {
                continue;
            }
            a.setFuncionario(f);
            a.setAnio(anio);
            a.setMesesTrabajados(mesesTrabajados);
            a.setMontoCalculado(monto);
            a.setEstado(AguinaldoEstado.CALCULADO);
            if (a.getCreadoEn() == null) a.setCreadoEn(LocalDateTime.now());
            repository.save(a);
            n++;
        }
        return n;
    }

    @Transactional
    public Aguinaldo aprobar(Long id) {
        Aguinaldo a = repository.findById(id)
                .orElseThrow(() -> new GraphQLException("Aguinaldo no encontrado"));
        a.setEstado(AguinaldoEstado.APROBADO);
        return repository.save(a);
    }

    @Override
    public Aguinaldo save(Aguinaldo entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEstado() == null) entity.setEstado(AguinaldoEstado.CALCULADO);
        return super.save(entity);
    }
}
