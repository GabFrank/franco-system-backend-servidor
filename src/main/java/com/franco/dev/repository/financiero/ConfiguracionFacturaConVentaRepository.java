package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturaConVenta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionFacturaConVentaRepository extends HelperRepository<ConfiguracionFacturaConVenta, Long> {

    default Class<ConfiguracionFacturaConVenta> getEntityClass() {
        return ConfiguracionFacturaConVenta.class;
    }
}
