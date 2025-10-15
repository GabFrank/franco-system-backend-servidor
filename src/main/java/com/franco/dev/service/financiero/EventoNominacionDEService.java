package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.financiero.EventoNominacionDERepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class EventoNominacionDEService extends CrudService<EventoNominacionDE, EventoNominacionDERepository, Long> {

    private final EventoNominacionDERepository repository;

    @Override
    public EventoNominacionDERepository getRepository() {
        return repository;
    }

    public List<EventoNominacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId) {
        return repository.findByDocumentoElectronicoId(documentoElectronicoId);
    }

    public Optional<EventoNominacionDE> findByDocumentoElectronicoIdAndEstado(Long documentoElectronicoId, EstadoEvento estado) {
        return repository.findByDocumentoElectronicoIdAndEstado(documentoElectronicoId, estado);
    }

    public List<EventoNominacionDE> findByEstado(EstadoEvento estado) {
        return repository.findByEstado(estado);
    }

    public List<EventoNominacionDE> findByDocumentoElectronicoIdOrderByFecha(Long documentoElectronicoId) {
        return repository.findByDocumentoElectronicoIdOrderByFecha(documentoElectronicoId);
    }

    @Override
    public EventoNominacionDE save(EventoNominacionDE entity) {
        return super.save(entity);
    }
}

