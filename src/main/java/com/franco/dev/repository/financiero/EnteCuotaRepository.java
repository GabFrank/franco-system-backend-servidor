package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EnteCuota;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EnteCuotaRepository extends HelperRepository<EnteCuota, Long> {

    default Class<EnteCuota> getEntityClass() {
        return EnteCuota.class;
    }

    @Query("select ec from EnteCuota ec where ec.enteFinanciero.id = ?1 order by ec.numeroCuota asc")
    List<EnteCuota> findByEnteFinancieroId(Long enteFinancieroId);

    @Query("select ec from EnteCuota ec where ec.enteFinanciero.id = ?1 and ec.pagado = false order by ec.numeroCuota asc")
    List<EnteCuota> findPendientesByEnteFinancieroId(Long enteFinancieroId);

    @Query(value = "select ec from EnteCuota ec where " +
            "(cast(:enteFinancieroId as long) is null or ec.enteFinanciero.id = :enteFinancieroId) " +
            "order by ec.numeroCuota asc",
            countQuery = "select count(ec) from EnteCuota ec where " +
            "(cast(:enteFinancieroId as long) is null or ec.enteFinanciero.id = :enteFinancieroId)")
    Page<EnteCuota> findAllByEnteFinancieroId(
            @org.springframework.data.repository.query.Param("enteFinancieroId") Long enteFinancieroId,
            Pageable pageable);
}
