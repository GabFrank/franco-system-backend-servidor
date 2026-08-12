package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface VentaCreditoCuotaRepository extends HelperRepository<VentaCreditoCuota, EmbebedPrimaryKey> {

    default Class<VentaCreditoCuota> getEntityClass() {
        return VentaCreditoCuota.class;
    }

    public List<VentaCreditoCuota> findAllByVentaCreditoIdAndSucursalId(Long id, Long sucId);

    /** Toma la cuota con lock pesimista para el cobro (serializa cobros parciales concurrentes). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VentaCreditoCuota c where c.id = :id and c.sucursalId = :sucId")
    Optional<VentaCreditoCuota> lockByIdAndSucursalId(@Param("id") Long id, @Param("sucId") Long sucId);

//    Moneda findByPaisId(Long id);

}