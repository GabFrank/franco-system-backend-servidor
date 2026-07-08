package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Aguinaldo;
import com.franco.dev.repository.HelperRepository;

import java.util.List;
import java.util.Optional;

public interface AguinaldoRepository extends HelperRepository<Aguinaldo, Long> {

    default Class<Aguinaldo> getEntityClass() {
        return Aguinaldo.class;
    }

    List<Aguinaldo> findByAnioOrderByIdAsc(Integer anio);

    List<Aguinaldo> findByFuncionarioIdOrderByAnioDesc(Long funcionarioId);

    Optional<Aguinaldo> findByFuncionarioIdAndAnio(Long funcionarioId, Integer anio);
}
