package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoCancelacionDERepository extends HelperRepository<EventoCancelacionDE, Long> {

    default Class<EventoCancelacionDE> getEntityClass() {
        return EventoCancelacionDE.class;
    }

    List<EventoCancelacionDE> findAllByOrderByIdDesc(Pageable pageable);

    List<EventoCancelacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId);

    Optional<EventoCancelacionDE> findByDocumentoElectronicoIdAndEstado(Long documentoElectronicoId, EstadoEvento estado);

    List<EventoCancelacionDE> findByEstado(EstadoEvento estado);

    @Query("SELECT e FROM EventoCancelacionDE e WHERE " +
           "e.documentoElectronico.id = :documentoElectronicoId " +
           "ORDER BY e.creadoEn DESC")
    List<EventoCancelacionDE> findByDocumentoElectronicoIdOrderByFecha(
        @Param("documentoElectronicoId") Long documentoElectronicoId
    );

    List<EventoCancelacionDE> findByCdcDocumentoAndActivo(String cdcDocumento, Boolean activo);

    Optional<EventoCancelacionDE> findByEventoId(String eventoId);
}

