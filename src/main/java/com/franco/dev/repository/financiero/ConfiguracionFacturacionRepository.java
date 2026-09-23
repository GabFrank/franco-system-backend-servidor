package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracionFacturacionRepository extends HelperRepository<ConfiguracionFacturacion, Long> {

    default Class<ConfiguracionFacturacion> getEntityClass() {
        return ConfiguracionFacturacion.class;
    }

    List<ConfiguracionFacturacion> findAllByOrderByIdAsc();

    /** La politica global. */
    Optional<ConfiguracionFacturacion> findFirstBySucursalIsNull();

    /** El override de una sucursal. */
    Optional<ConfiguracionFacturacion> findFirstBySucursalId(Long sucursalId);

    /** Las filas de sucursal (nunca la global) que no estan ya en ese estado: el masivo. */
    List<ConfiguracionFacturacion> findBySucursalIsNotNullAndActivoNotOrderByIdAsc(Boolean activo);
}
