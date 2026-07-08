package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.HoraExtra;
import com.franco.dev.repository.HelperRepository;

import java.time.LocalDate;
import java.util.List;

public interface HoraExtraRepository extends HelperRepository<HoraExtra, Long> {

    default Class<HoraExtra> getEntityClass() {
        return HoraExtra.class;
    }

    List<HoraExtra> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<HoraExtra> findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<HoraExtra> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);
    java.util.List<com.franco.dev.domain.rrhh.HoraExtra> findByFechaBetweenAndAnuladaFalse(java.time.LocalDate desde, java.time.LocalDate hasta);
}
