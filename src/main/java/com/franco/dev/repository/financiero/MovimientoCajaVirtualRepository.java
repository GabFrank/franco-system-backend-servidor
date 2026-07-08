package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoCajaVirtualRepository extends JpaRepository<MovimientoCajaVirtual, Long> {

    Page<MovimientoCajaVirtual> findByCajaVirtualIdOrderByCreadoEnDesc(Long cajaVirtualId, Pageable pageable);

    Page<MovimientoCajaVirtual> findByCajaVirtualIdAndCreadoEnBetweenOrderByCreadoEnDesc(
            Long cajaVirtualId, LocalDateTime inicio, LocalDateTime fin, Pageable pageable);

    List<MovimientoCajaVirtual> findByCajaVirtualIdAndActivoTrue(Long cajaVirtualId);
}
