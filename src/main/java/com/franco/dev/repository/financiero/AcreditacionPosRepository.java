package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.AcreditacionPos;
import com.franco.dev.domain.financiero.enums.EstadoAcreditacionPos;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.List;

public interface AcreditacionPosRepository extends JpaRepository<AcreditacionPos, Long> {
    List<AcreditacionPos> findByEstadoAndFechaEsperadaAcreditacionLessThanEqual(
            EstadoAcreditacionPos estado, LocalDateTime hasta, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AcreditacionPos a where a.id = :id")
    Optional<AcreditacionPos> lockById(@Param("id") Long id);

    /** Suma del monto esperado de acreditaciones POS pendientes de una cuenta (saldo "futuro"). */
    @Query("select coalesce(sum(a.montoEsperado), 0) from AcreditacionPos a "
            + "where a.cuentaBancaria.id = :cbId and a.estado = com.franco.dev.domain.financiero.enums.EstadoAcreditacionPos.PENDIENTE")
    java.math.BigDecimal sumEsperadoPendienteByCuenta(@Param("cbId") Long cbId);
}
