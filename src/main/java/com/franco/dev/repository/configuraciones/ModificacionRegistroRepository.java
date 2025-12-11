package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionRegistro;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ModificacionRegistroRepository extends HelperRepository<ModificacionRegistro, Long> {

    default Class<ModificacionRegistro> getEntityClass() {
        return ModificacionRegistro.class;
    }

    @Query("SELECT m FROM ModificacionRegistro m WHERE m.schemaNombre = :schemaNombre " +
            "AND m.modificadoEn BETWEEN :inicio AND :fin " +
            "AND m.activo = true " +
            "ORDER BY m.modificadoEn DESC")
    Page<ModificacionRegistro> findBySchemaNombreAndFechaBetween(
            @Param("schemaNombre") String schemaNombre,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            Pageable pageable
    );

    @Query("SELECT m FROM ModificacionRegistro m WHERE m.tipoEntidad = :tipoEntidad " +
            "AND m.activo = true " +
            "ORDER BY m.modificadoEn DESC")
    Page<ModificacionRegistro> findByTipoEntidad(
            @Param("tipoEntidad") String tipoEntidad,
            Pageable pageable
    );

    @Query("SELECT m FROM ModificacionRegistro m WHERE m.tipoEntidad = :tipoEntidad " +
            "AND m.schemaNombre = :schemaNombre " +
            "AND m.modificadoEn BETWEEN :inicio AND :fin " +
            "AND m.activo = true " +
            "ORDER BY m.modificadoEn DESC")
    Page<ModificacionRegistro> findByTipoEntidadAndSchemaAndFechaBetween(
            @Param("tipoEntidad") String tipoEntidad,
            @Param("schemaNombre") String schemaNombre,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            Pageable pageable
    );

    @Query("SELECT m FROM ModificacionRegistro m WHERE m.entidadId = :entidadId " +
            "AND m.tipoEntidad = :tipoEntidad " +
            "AND m.activo = true " +
            "ORDER BY m.modificadoEn DESC")
    List<ModificacionRegistro> findByEntidadIdAndTipoEntidad(
            @Param("entidadId") Long entidadId,
            @Param("tipoEntidad") String tipoEntidad
    );
}

