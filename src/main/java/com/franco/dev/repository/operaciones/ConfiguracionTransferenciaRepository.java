package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ConfiguracionTransferencia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionTransferenciaRepository extends HelperRepository<ConfiguracionTransferencia, Long> {

    default Class<ConfiguracionTransferencia> getEntityClass() {
        return ConfiguracionTransferencia.class;
    }
}
