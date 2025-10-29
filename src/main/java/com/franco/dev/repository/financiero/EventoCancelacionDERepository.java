package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoCancelacionDERepository extends HelperRepository<EventoCancelacionDE, EmbebedPrimaryKey> {

    default Class<EventoCancelacionDE> getEntityClass() {
        return EventoCancelacionDE.class;
    }

    List<EventoCancelacionDE> findAllByOrderByIdDesc(Pageable pageable);

    @Query("SELECT e FROM EventoCancelacionDE e " +
           "LEFT JOIN e.documentoElectronico d " +
           "WHERE d.id = :documentoElectronicoId AND e.sucursalId = :sucursalId")
    List<EventoCancelacionDE> findByDocumentoElectronicoIdAndSucursalId(
        @Param("documentoElectronicoId") Long documentoElectronicoId, 
        @Param("sucursalId") Long sucursalId);

    @Query("SELECT e FROM EventoCancelacionDE e " +
           "LEFT JOIN e.documentoElectronico d " +
           "WHERE d.id = :documentoElectronicoId AND e.sucursalId = :sucursalId AND e.estado = :estado")
    Optional<EventoCancelacionDE> findByDocumentoElectronicoIdAndSucursalIdAndEstado(
        @Param("documentoElectronicoId") Long documentoElectronicoId, 
        @Param("sucursalId") Long sucursalId, 
        @Param("estado") EstadoEvento estado);

    List<EventoCancelacionDE> findByEstadoAndSucursalId(EstadoEvento estado, Long sucursalId);

    @Query("SELECT e FROM EventoCancelacionDE e " +
           "LEFT JOIN e.documentoElectronico d " +
           "WHERE d.id = :documentoElectronicoId AND e.sucursalId = :sucursalId " +
           "ORDER BY e.creadoEn DESC")
    List<EventoCancelacionDE> findByDocumentoElectronicoIdOrderByFechaAndSucursalId(
        @Param("documentoElectronicoId") Long documentoElectronicoId,
        @Param("sucursalId") Long sucursalId
    );

    List<EventoCancelacionDE> findByCdcDocumentoAndActivo(String cdcDocumento, Boolean activo);

    Optional<EventoCancelacionDE> findByEventoIdAndSucursalId(String eventoId, Long sucursalId);

    List<EventoCancelacionDE> findByEstadoAndActivo(EstadoEvento estado, Boolean activo);
}

