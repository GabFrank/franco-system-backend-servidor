package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CajaVirtualSaldoRepository extends JpaRepository<CajaVirtualSaldo, Long> {

    Optional<CajaVirtualSaldo> findByCajaVirtualIdAndMonedaId(Long cajaVirtualId, Long monedaId);

    List<CajaVirtualSaldo> findByCajaVirtualId(Long cajaVirtualId);

    /**
     * Toma la fila de saldo con lock pesimista de escritura (SELECT ... FOR UPDATE).
     * Serializa dos transacciones concurrentes que tocan el mismo (caja, moneda),
     * evitando el lost-update del read-modify-write. El caller debe lockear en
     * **orden canónico** (por id de caja ascendente) cuando toca dos cajas
     * (transferencia) para no generar deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CajaVirtualSaldo s where s.cajaVirtual.id = :cajaId and s.moneda.id = :monedaId")
    Optional<CajaVirtualSaldo> lockByCajaVirtualIdAndMonedaId(@Param("cajaId") Long cajaId, @Param("monedaId") Long monedaId);
}
