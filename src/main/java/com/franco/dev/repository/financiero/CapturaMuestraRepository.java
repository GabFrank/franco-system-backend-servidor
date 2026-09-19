package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CapturaMuestra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CapturaMuestraRepository extends JpaRepository<CapturaMuestra, Long> {

    /** Las muestras de un formato, la mas nueva primero. Es el orden en el que se miran. */
    List<CapturaMuestra> findByFormatoTerminalPosIdOrderByCreadoEnDesc(Long formatoTerminalPosId);

    /** Para la purga por antiguedad. */
    List<CapturaMuestra> findByCreadoEnBefore(LocalDateTime limite);
}
