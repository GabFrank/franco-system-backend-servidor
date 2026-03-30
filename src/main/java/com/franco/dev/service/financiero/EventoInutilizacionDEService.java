package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EventoInutilizacionDE;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.repository.financiero.EventoInutilizacionDERepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class EventoInutilizacionDEService extends CrudService<EventoInutilizacionDE, EventoInutilizacionDERepository, Long> {

    private final EventoInutilizacionDERepository repository;

    @Override
    public EventoInutilizacionDERepository getRepository() {
        return repository;
    }

    public List<EventoInutilizacionDE> findByTimbradoIdAndSucursalId(Long timbradoId, Long sucursalId) {
        return repository.findByTimbradoIdAndSucursalId(timbradoId, sucursalId);
    }

    public List<EventoInutilizacionDE> findByEstadoAndSucursalId(EstadoEvento estado, Long sucursalId) {
        return repository.findByEstadoAndSucursalId(estado, sucursalId);
    }

    public List<EventoInutilizacionDE> findByTimbradoDetalleIdAndSucursalId(Long timbradoDetalleId, Long sucursalId) {
        return repository.findByTimbradoDetalleIdAndSucursalId(timbradoDetalleId, sucursalId);
    }

    public Optional<EventoInutilizacionDE> findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public Optional<EventoInutilizacionDE> findByEventoIdAndSucursalId(String eventoId, Long sucursalId) {
        return repository.findByEventoIdAndSucursalId(eventoId, sucursalId);
    }

    public List<EventoInutilizacionDE> findByEstadoAndActivo(EstadoEvento estado, Boolean activo) {
        return repository.findByEstadoAndActivo(estado, activo);
    }

    public List<EventoInutilizacionDE> findBySucursalIdOrderByCreadoEnDesc(Long sucursalId) {
        return repository.findBySucursalIdOrderByCreadoEnDesc(sucursalId);
    }

    public Boolean deleteByIdAndSucursalId(Long id, Long sucId) {
        return repository.deleteByIdAndSucursalId(id, sucId);
    }

    @Override
    public EventoInutilizacionDE save(EventoInutilizacionDE entity) {
        return super.save(entity);
    }

    public Page<EventoInutilizacionDE> findWithFilters(
            Long sucursalId,
            Long timbradoId,
            EstadoEvento estado,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findWithFilters(sucursalId, timbradoId, estado, fechaInicio, fechaFin, pageable);
    }
}

