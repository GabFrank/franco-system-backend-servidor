package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoNominacionDERepository extends HelperRepository<EventoNominacionDE, Long> {

    default Class<EventoNominacionDE> getEntityClass() {
        return EventoNominacionDE.class;
    }

    List<EventoNominacionDE> findAllByOrderByIdDesc(Pageable pageable);

    @Query("SELECT e FROM EventoNominacionDE e " +
           "LEFT JOIN e.documentoElectronico d " +
           "WHERE d.id = :documentoElectronicoId AND e.sucursalId = :sucursalId")
    List<EventoNominacionDE> findByDocumentoElectronicoIdAndSucursalId(
        @Param("documentoElectronicoId") Long documentoElectronicoId, 
        @Param("sucursalId") Long sucursalId);

    @Query("SELECT e FROM EventoNominacionDE e " +
           "LEFT JOIN e.documentoElectronico d " +
           "WHERE d.id = :documentoElectronicoId AND e.sucursalId = :sucursalId AND e.estado = :estado")
    Optional<EventoNominacionDE> findByDocumentoElectronicoIdAndSucursalIdAndEstado(
        @Param("documentoElectronicoId") Long documentoElectronicoId, 
        @Param("sucursalId") Long sucursalId, 
        @Param("estado") EstadoEvento estado);

    List<EventoNominacionDE> findByEstado(EstadoEvento estado);

    @Query("SELECT e FROM EventoNominacionDE e " +
           "LEFT JOIN e.documentoElectronico d " +
           "WHERE d.id = :documentoElectronicoId AND e.sucursalId = :sucursalId " +
           "ORDER BY e.creadoEn DESC")
    List<EventoNominacionDE> findByDocumentoElectronicoIdAndSucursalIdOrderByFecha(
        @Param("documentoElectronicoId") Long documentoElectronicoId,
        @Param("sucursalId") Long sucursalId
    );

    List<EventoNominacionDE> findByCdcDocumentoAndActivo(String cdcDocumento, Boolean activo);

    // findByEventoIdAndSucursalId
    Optional<EventoNominacionDE> findByEventoIdAndSucursalId(String eventoId, Long sucursalId);

    Boolean deleteByIdAndSucursalId(Long id, Long sucId);

    Optional<EventoNominacionDE> findByIdAndSucursalId(Long id, Long sucId);
}

