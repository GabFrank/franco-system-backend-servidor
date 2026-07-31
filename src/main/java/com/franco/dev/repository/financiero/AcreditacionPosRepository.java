package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.AcreditacionPos;
import com.franco.dev.domain.financiero.enums.EstadoAcreditacionPos;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AcreditacionPosRepository extends JpaRepository<AcreditacionPos, Long> {
    List<AcreditacionPos> findByEstadoAndFechaEsperadaAcreditacionLessThanEqual(
            EstadoAcreditacionPos estado, LocalDateTime hasta, Pageable pageable);
}
