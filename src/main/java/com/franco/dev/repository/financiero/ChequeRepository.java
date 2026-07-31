package com.franco.dev.repository.financiero;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.Optional;
import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChequeRepository extends HelperRepository<Cheque, Long> {
    default Class<Cheque> getEntityClass() {
        return Cheque.class;
    }

    @Query("select c from Cheque c " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(CAST(numero as text)) like %?1% or UPPER(orden) like %?1% or UPPER(concepto) like %?1%")
    public List<Cheque> findByAll(String texto);
    
    List<Cheque> findByChequeraId(Long chequeraId);
    
    Cheque findByPagoDetalleCuotaId(Long pagoDetalleCuotaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Cheque e where e.id = :id")
    Optional<Cheque> lockById(@Param("id") Long id);

}
