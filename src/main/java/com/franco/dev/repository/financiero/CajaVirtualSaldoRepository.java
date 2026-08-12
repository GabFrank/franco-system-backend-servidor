package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CajaVirtualSaldoRepository extends JpaRepository<CajaVirtualSaldo, Long> {

    Optional<CajaVirtualSaldo> findByCajaVirtualIdAndMonedaId(Long cajaVirtualId, Long monedaId);

    /** Crea la fila de saldo (caja, moneda) en 0 si no existe. Idempotente; evita carrera en el primer movimiento. */
    @Modifying
    @Query(value = "INSERT INTO financiero.caja_virtual_saldo (caja_virtual_id, moneda_id, saldo, creado_en) " +
            "VALUES (:cajaId, :monedaId, 0, now()) ON CONFLICT (caja_virtual_id, moneda_id) DO NOTHING", nativeQuery = true)
    void ensureRow(@Param("cajaId") Long cajaId, @Param("monedaId") Long monedaId);

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

    /** Saldo total de efectivo por moneda (todas las cajas). Fila: [monedaId, denominacion, saldo]. */
    @Query("select s.moneda.id, s.moneda.denominacion, coalesce(sum(s.saldo),0) " +
            "from CajaVirtualSaldo s group by s.moneda.id, s.moneda.denominacion")
    List<Object[]> saldoConsolidadoPorMoneda();
}
