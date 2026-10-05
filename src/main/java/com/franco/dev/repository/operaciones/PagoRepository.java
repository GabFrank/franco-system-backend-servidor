package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.enums.PagoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface PagoRepository extends HelperRepository<Pago, Long> {
    default Class<Pago> getEntityClass() {
        return Pago.class;
    }

    /** Toma el pago con lock pesimista: serializa dos anulaciones simultaneas del mismo evento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.id = :id")
    Optional<Pago> lockById(@Param("id") Long id);

    /**
     * Solo el estado, leido de la base: si el pago ya estaba en el contexto de persistencia,
     * {@link #lockById} espera el lock pero devuelve esa instancia sin refrescar (estado viejo).
     */
    @Query("select p.estado from Pago p where p.id = :id")
    Optional<PagoEstado> findEstadoById(@Param("id") Long id);
}
