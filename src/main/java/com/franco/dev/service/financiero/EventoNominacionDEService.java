package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
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

    public List<EventoNominacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId, Long sucursalId) {
        return repository.findByDocumentoElectronicoIdAndSucursalId(documentoElectronicoId, sucursalId);
    }

    public Optional<EventoNominacionDE> findByDocumentoElectronicoIdAndEstado(Long documentoElectronicoId, Long sucursalId, EstadoEvento estado) {
        return repository.findByDocumentoElectronicoIdAndSucursalIdAndEstado(documentoElectronicoId, sucursalId, estado);
    }

    public List<EventoNominacionDE> findByEstado(EstadoEvento estado) {
        return repository.findByEstado(estado);
    }

    public List<EventoNominacionDE> findByDocumentoElectronicoIdOrderByFecha(Long documentoElectronicoId, Long sucursalId) {
        return repository.findByDocumentoElectronicoIdAndSucursalIdOrderByFecha(documentoElectronicoId, sucursalId);
    }

    public boolean tieneNominacionAprobada(Long documentoElectronicoId, Long sucursalId) {
        return repository.findByDocumentoElectronicoIdAndSucursalIdAndEstado(documentoElectronicoId, sucursalId, EstadoEvento.APROBADO).isPresent();
    }

    public List<EventoNominacionDE> findActivosByCdcDocumento(String cdcDocumento) {
        return repository.findByCdcDocumentoAndActivo(cdcDocumento, true);
    }

    public Optional<EventoNominacionDE> findByEventoId(String eventoId, Long sucursalId) {
        return repository.findByEventoIdAndSucursalId(eventoId, sucursalId);
    }

    public Boolean deleteByIdAndSucursalId(Long id, Long sucId) {
        return repository.deleteByIdAndSucursalId(id, sucId);
    }

    public Optional<EventoNominacionDE> findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    @Override
    public EventoNominacionDE save(EventoNominacionDE entity) {
        return super.save(entity);
    }
}

