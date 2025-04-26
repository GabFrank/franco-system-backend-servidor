package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoPersonas;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoPersonasRepository extends HelperRepository<MovimientoPersonas, Long> {

    default Class<MovimientoPersonas> getEntityClass() {
        return MovimientoPersonas.class;
    }

    public List<MovimientoPersonas> findAllByPersonaIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByIdAsc(Long id, LocalDateTime start, LocalDateTime end);

    public List<MovimientoPersonas> findAllByPersonaIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualAndActivoOrderByIdAsc(Long id, LocalDateTime start, LocalDateTime end, Boolean activo);

    @Query(value = "select sum(mp.valor_total) from financiero.movimiento_personas mp " +
            "where mp.persona_id = ?1 and mp.vencimiento <= ?2 and mp.activo = true", nativeQuery = true)
    public Double getSaldoPorPersona(Long id, LocalDateTime vencimiento);

}