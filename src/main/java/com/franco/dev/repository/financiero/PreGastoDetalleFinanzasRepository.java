package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.PreGastoDetalleFinanzas;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PreGastoDetalleFinanzasRepository extends HelperRepository<PreGastoDetalleFinanzas, Long> {
    
    @Query(value = "select * from financiero.pre_gasto_detalle_finanzas where pre_gasto_id = ?1 and sucursal_id = ?2", nativeQuery = true)
    List<PreGastoDetalleFinanzas> findByPreGastoIdAndSucursalId(Long preGastoId, Long sucursalId);
}
