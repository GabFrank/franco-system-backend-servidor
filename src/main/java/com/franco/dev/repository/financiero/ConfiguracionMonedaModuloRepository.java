package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguracionMonedaModuloRepository extends HelperRepository<ConfiguracionMonedaModulo, Long> {

    default Class<ConfiguracionMonedaModulo> getEntityClass() {
        return ConfiguracionMonedaModulo.class;
    }

    /** Todas las filas de un modulo, la mas reciente primero (vigencia y luego id). */
    List<ConfiguracionMonedaModulo> findByModuloOrderByVigenteDesdeDescIdDesc(ModuloMoneda modulo);

    /** Todas, la mas reciente primero: la pantalla de administracion. */
    List<ConfiguracionMonedaModulo> findAllByOrderByVigenteDesdeDescIdDesc();
}
