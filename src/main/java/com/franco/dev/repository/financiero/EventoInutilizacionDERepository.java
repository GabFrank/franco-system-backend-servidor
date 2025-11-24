package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoInutilizacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventoInutilizacionDERepository extends HelperRepository<EventoInutilizacionDE, Long> {

    default Class<EventoInutilizacionDE> getEntityClass() {
        return EventoInutilizacionDE.class;
    }

    List<EventoInutilizacionDE> findAllByOrderByIdDesc(Pageable pageable);

    @Query("SELECT e FROM EventoInutilizacionDE e WHERE e.timbradoId = :timbradoId AND e.sucursalId = :sucursalId")
    List<EventoInutilizacionDE> findByTimbradoIdAndSucursalId(
        @Param("timbradoId") Long timbradoId, 
        @Param("sucursalId") Long sucursalId);

    List<EventoInutilizacionDE> findByEstadoAndSucursalId(EstadoEvento estado, Long sucursalId);

    @Query("SELECT e FROM EventoInutilizacionDE e WHERE e.timbradoDetalleId = :timbradoDetalleId AND e.sucursalId = :sucursalId")
    List<EventoInutilizacionDE> findByTimbradoDetalleIdAndSucursalId(
        @Param("timbradoDetalleId") Long timbradoDetalleId, 
        @Param("sucursalId") Long sucursalId);

    Optional<EventoInutilizacionDE> findByIdAndSucursalId(Long id, Long sucId);

    Optional<EventoInutilizacionDE> findByEventoIdAndSucursalId(String eventoId, Long sucursalId);

    List<EventoInutilizacionDE> findByEstadoAndActivo(EstadoEvento estado, Boolean activo);

    List<EventoInutilizacionDE> findBySucursalIdOrderByCreadoEnDesc(Long sucursalId);

    Boolean deleteByIdAndSucursalId(Long id, Long sucId);

    @Query("SELECT e FROM EventoInutilizacionDE e WHERE " +
           "(:sucursalId IS NULL OR e.sucursalId = :sucursalId) AND " +
           "(:timbradoId IS NULL OR e.timbrado.id = :timbradoId) AND " +
           "(e.estado = :estado OR cast(:estado as com.franco.dev.domain.financiero.enums.EstadoEvento) IS NULL) AND " +
           "(e.creadoEn >= :fechaInicio OR cast(:fechaInicio as timestamp) IS NULL) AND " +
           "(e.creadoEn <= :fechaFin OR cast(:fechaFin as timestamp) IS NULL) AND " +
           "(e.activo = true) " +
           "ORDER BY e.creadoEn DESC")
    Page<EventoInutilizacionDE> findWithFilters(
            @Param("sucursalId") Long sucursalId,
            @Param("timbradoId") Long timbradoId,
            @Param("estado") EstadoEvento estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );
}

