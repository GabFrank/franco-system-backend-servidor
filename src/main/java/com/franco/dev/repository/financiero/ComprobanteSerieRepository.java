package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ComprobanteSerie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface ComprobanteSerieRepository extends JpaRepository<ComprobanteSerie, Long> {

    Optional<ComprobanteSerie> findByTipo(String tipo);

    /** Toma la serie con lock pesimista para incrementar el correlativo sin colisión. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ComprobanteSerie s where s.tipo = :tipo")
    Optional<ComprobanteSerie> lockByTipo(@Param("tipo") String tipo);
}
