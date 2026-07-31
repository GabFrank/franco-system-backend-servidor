package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CuentaBancariaRepository extends HelperRepository<CuentaBancaria, Long> {

    default Class<CuentaBancariaRepository> getEntityClass() {
        return CuentaBancariaRepository.class;
    }

    @Query("select m from CuentaBancaria m " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(numero) like %?1%")
    public List<CuentaBancaria> findByAll(String texto);

    /** Toma la cuenta con lock pesimista para mutar su saldo sin lost-update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaBancaria c where c.id = :id")
    Optional<CuentaBancaria> lockById(@Param("id") Long id);

}