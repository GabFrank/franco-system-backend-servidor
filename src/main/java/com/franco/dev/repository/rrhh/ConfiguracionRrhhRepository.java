package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.ConfiguracionRrhh;
import com.franco.dev.domain.rrhh.enums.ConfiguracionRrhhTipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select c from ConfiguracionRrhh c where " +
            "(:texto is null or upper(c.clave) like concat('%', upper(:texto), '%') or upper(c.descripcion) like concat('%', upper(:texto), '%')) and " +
            "(:tipo is null or c.tipo = :tipo) " +
            "order by c.clave asc")
    Page<ConfiguracionRrhh> findPage(@Param("texto") String texto,
                                     @Param("tipo") ConfiguracionRrhhTipo tipo,
                                     Pageable pageable);
}
