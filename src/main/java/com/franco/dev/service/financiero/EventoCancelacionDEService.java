package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
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
public class EventoCancelacionDEService extends CrudService<EventoCancelacionDE, EventoCancelacionDERepository, EmbebedPrimaryKey> {

    private final EventoCancelacionDERepository repository;

    @Override
    public EventoCancelacionDERepository getRepository() {
        return repository;
    }

    public List<EventoCancelacionDE> findByDocumentoElectronicoId(Long documentoElectronicoId, Long sucursalId) {
        return repository.findByDocumentoElectronicoIdAndSucursalId(documentoElectronicoId, sucursalId);
    }

    public Optional<EventoCancelacionDE> findByDocumentoElectronicoIdAndEstado(Long documentoElectronicoId, Long sucursalId, EstadoEvento estado) {
        return repository.findByDocumentoElectronicoIdAndSucursalIdAndEstado(documentoElectronicoId, sucursalId, estado);
    }

    public List<EventoCancelacionDE> findByEstado(EstadoEvento estado, Long sucursalId) {
        return repository.findByEstadoAndSucursalId(estado, sucursalId);
    }

    public List<EventoCancelacionDE> findByDocumentoElectronicoIdOrderByFecha(Long documentoElectronicoId, Long sucursalId) {
        return repository.findByDocumentoElectronicoIdOrderByFechaAndSucursalId(documentoElectronicoId, sucursalId);
    }

    public boolean tieneCancelacionAprobada(Long documentoElectronicoId, Long sucursalId) {
        return repository.findByDocumentoElectronicoIdAndSucursalIdAndEstado(documentoElectronicoId, sucursalId, EstadoEvento.APROBADO).isPresent();
    }

    public List<EventoCancelacionDE> findActivosByCdcDocumento(String cdcDocumento) {
        return repository.findByCdcDocumentoAndActivo(cdcDocumento, true);
    }

    public Optional<EventoCancelacionDE> findByEventoId(String eventoId, Long sucursalId) {
        return repository.findByEventoIdAndSucursalId(eventoId, sucursalId);
    }

    @Override
    public EventoCancelacionDE save(EventoCancelacionDE entity) {
        return super.save(entity);
    }
}

