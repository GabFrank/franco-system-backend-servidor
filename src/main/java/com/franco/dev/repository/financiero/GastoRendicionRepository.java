package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.GastoRendicion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GastoRendicionRepository extends HelperRepository<GastoRendicion, Long> {
    @Query(value = "SELECT * FROM financiero.gasto_rendicion g WHERE g.pre_gasto_id = ?1 AND g.sucursal_id = ?2", nativeQuery = true)
    List<GastoRendicion> findByPreGastoIdAndSucursalId(Long preGastoId, Long sucursalId);
}
