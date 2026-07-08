package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PrestamoRepository extends HelperRepository<Prestamo, Long> {

    default Class<Prestamo> getEntityClass() {
        return Prestamo.class;
    }

    List<Prestamo> findByFuncionarioIdOrderByFechaInicioDesc(Long funcionarioId);

    List<Prestamo> findByEstadoOrderByFechaInicioDesc(PrestamoEstado estado);
}
