package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.ConfiguracionRrhh;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConfiguracionRrhhRepository extends HelperRepository<ConfiguracionRrhh, Long> {

    default Class<ConfiguracionRrhh> getEntityClass() {
        return ConfiguracionRrhh.class;
    }

    Optional<ConfiguracionRrhh> findByClave(String clave);

    @Query(value = "select * from rrhh.configuracion_rrhh c " +
            "where CAST(c.id as text) like concat('%', ?1, '%') " +
            "or upper(c.clave) like concat('%', ?1, '%') " +
            "or upper(c.descripcion) like concat('%', ?1, '%') " +
            "order by c.clave asc", nativeQuery = true)
    List<ConfiguracionRrhh> findByAll(String texto);
}
