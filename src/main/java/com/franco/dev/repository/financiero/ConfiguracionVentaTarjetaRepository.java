package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionVentaTarjetaRepository extends HelperRepository<ConfiguracionVentaTarjeta, Long> {

    default Class<ConfiguracionVentaTarjeta> getEntityClass() {
        return ConfiguracionVentaTarjeta.class;
    }
}
