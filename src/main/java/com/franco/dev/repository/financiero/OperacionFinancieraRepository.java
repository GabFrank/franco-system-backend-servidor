package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.OperacionFinanciera;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface OperacionFinancieraRepository extends JpaRepository<OperacionFinanciera, Long> {
    Page<OperacionFinanciera> findAllByOrderByCreadoEnDesc(Pageable pageable);

    /** Toma la operacion con lock pesimista: serializa dos anulaciones simultaneas de la misma. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacionFinanciera o where o.id = :id")
    Optional<OperacionFinanciera> lockById(@Param("id") Long id);

    /**
     * Solo si esta anulada, leido de la base: {@link #lockById} devuelve la instancia que ya estuviera
     * cargada, sin refrescar. Se llama <b>despues</b> del lock.
     */
    @Query("select coalesce(o.anulado, false) from OperacionFinanciera o where o.id = :id")
    Optional<Boolean> findAnuladoById(@Param("id") Long id);
}
