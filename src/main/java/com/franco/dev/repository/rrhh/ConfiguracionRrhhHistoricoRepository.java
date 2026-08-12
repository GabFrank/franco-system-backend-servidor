package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.ConfiguracionRrhhHistorico;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface ConfiguracionRrhhHistoricoRepository extends HelperRepository<ConfiguracionRrhhHistorico, Long> {

    default Class<ConfiguracionRrhhHistorico> getEntityClass() {
        return ConfiguracionRrhhHistorico.class;
    }

    List<ConfiguracionRrhhHistorico> findByClaveOrderByCreadoEnDesc(String clave);

    List<ConfiguracionRrhhHistorico> findByConfiguracionRrhhIdOrderByCreadoEnDesc(Long configuracionRrhhId);
}
