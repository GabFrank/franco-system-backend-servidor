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

    List<EventoNominacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId);

    Optional<EventoNominacionDE> findByDocumentoElectronicoIdAndEstado(Long documentoElectronicoId, EstadoEvento estado);

    List<EventoNominacionDE> findByEstado(EstadoEvento estado);

    @Query("SELECT e FROM EventoNominacionDE e WHERE " +
           "e.documentoElectronico.id = :documentoElectronicoId " +
           "ORDER BY e.creadoEn DESC")
    List<EventoNominacionDE> findByDocumentoElectronicoIdOrderByFecha(
        @Param("documentoElectronicoId") Long documentoElectronicoId
    );

    List<EventoNominacionDE> findByCdcDocumentoAndActivo(String cdcDocumento, Boolean activo);

    Optional<EventoNominacionDE> findByEventoId(String eventoId);
}

