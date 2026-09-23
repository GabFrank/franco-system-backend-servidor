package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacionHistorial;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguracionFacturacionHistorialRepository extends HelperRepository<ConfiguracionFacturacionHistorial, Long> {

    default Class<ConfiguracionFacturacionHistorial> getEntityClass() {
        return ConfiguracionFacturacionHistorial.class;
    }

    List<ConfiguracionFacturacionHistorial> findAllByOrderByIdDesc(Pageable pageable);

    List<ConfiguracionFacturacionHistorial> findBySucursalIdOrderByIdDesc(Long sucursalId, Pageable pageable);

    /** La global no tiene sucursal: su historial se pide aparte. */
    List<ConfiguracionFacturacionHistorial> findBySucursalIsNullOrderByIdDesc(Pageable pageable);
}
