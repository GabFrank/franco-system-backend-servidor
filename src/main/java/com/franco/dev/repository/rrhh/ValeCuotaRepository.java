package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.ValeCuota;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ValeCuotaRepository extends HelperRepository<ValeCuota, Long> {

    default Class<ValeCuota> getEntityClass() {
        return ValeCuota.class;
    }

    List<ValeCuota> findByValeIdOrderByNumeroAsc(Long valeId);

    /** Toma la cuota con lock pesimista al descontarla: dos pagos de la misma cuota se serializan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ValeCuota c where c.id = :id")
    Optional<ValeCuota> lockById(@Param("id") Long id);
}
