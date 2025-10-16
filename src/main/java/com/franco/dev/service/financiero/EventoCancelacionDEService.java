package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.financiero.EventoCancelacionDERepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class EventoCancelacionDEService extends CrudService<EventoCancelacionDE, EventoCancelacionDERepository, Long> {

    private final EventoCancelacionDERepository repository;

    @Override
    public EventoCancelacionDERepository getRepository() {
        return repository;
    }

    public List<EventoCancelacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId) {
        return repository.findByDocumentoElectronicoId(documentoElectronicoId);
    }

    public Optional<EventoCancelacionDE> findByDocumentoElectronicoIdAndEstado(Long documentoElectronicoId, EstadoEvento estado) {
        return repository.findByDocumentoElectronicoIdAndEstado(documentoElectronicoId, estado);
    }

    public List<EventoCancelacionDE> findByEstado(EstadoEvento estado) {
        return repository.findByEstado(estado);
    }

    public List<EventoCancelacionDE> findByDocumentoElectronicoIdOrderByFecha(Long documentoElectronicoId) {
        return repository.findByDocumentoElectronicoIdOrderByFecha(documentoElectronicoId);
    }

    public boolean tieneCancelacionAprobada(Long documentoElectronicoId) {
        return repository.findByDocumentoElectronicoIdAndEstado(documentoElectronicoId, EstadoEvento.APROBADO).isPresent();
    }

    public List<EventoCancelacionDE> findActivosByCdcDocumento(String cdcDocumento) {
        return repository.findByCdcDocumentoAndActivo(cdcDocumento, true);
    }

    public Optional<EventoCancelacionDE> findByEventoId(String eventoId) {
        return repository.findByEventoId(eventoId);
    }

    @Override
    public EventoCancelacionDE save(EventoCancelacionDE entity) {
        return super.save(entity);
    }
}

