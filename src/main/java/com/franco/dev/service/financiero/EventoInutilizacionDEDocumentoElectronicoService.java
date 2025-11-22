package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.EventoInutilizacionDEDocumentoElectronico;
import com.franco.dev.repository.financiero.EventoInutilizacionDEDocumentoElectronicoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service para gestionar las relaciones entre EventoInutilizacionDE y DocumentoElectronico.
 */
@Slf4j
@Service
@AllArgsConstructor
public class EventoInutilizacionDEDocumentoElectronicoService 
        extends CrudService<EventoInutilizacionDEDocumentoElectronico, EventoInutilizacionDEDocumentoElectronicoRepository, Long> {

    private final EventoInutilizacionDEDocumentoElectronicoRepository repository;

    @Override
    public EventoInutilizacionDEDocumentoElectronicoRepository getRepository() {
        return repository;
    }

    /**
     * Buscar todas las relaciones para un evento de inutilización
     */
    public List<EventoInutilizacionDEDocumentoElectronico> findByEventoInutilizacionDeIdAndSucursalId(
            Long eventoId, 
            Long sucursalId) {
        return repository.findByEventoInutilizacionDeIdAndSucursalId(eventoId, sucursalId);
    }

    /**
     * Buscar todas las relaciones para un documento electrónico
     */
    public List<EventoInutilizacionDEDocumentoElectronico> findByDocumentoElectronicoIdAndSucursalId(
            Long documentoId, 
            Long sucursalId) {
        return repository.findByDocumentoElectronicoIdAndSucursalId(documentoId, sucursalId);
    }

    /**
     * Crear una relación entre un evento y un documento electrónico.
     * Si la relación ya existe, no la crea nuevamente.
     */
    @Transactional
    public EventoInutilizacionDEDocumentoElectronico crearRelacion(
            Long eventoId,
            Long eventoSucursalId,
            Long documentoId,
            Long documentoSucursalId) {
        
        // Verificar si ya existe la relación
        if (repository.existsByEventoAndDocumento(eventoId, eventoSucursalId, documentoId, documentoSucursalId)) {
            log.debug("La relación entre evento {} y documento {} ya existe", eventoId, documentoId);
            return null; // Ya existe, no crear duplicado
        }

        // Crear nueva relación
        EventoInutilizacionDEDocumentoElectronico relacion = new EventoInutilizacionDEDocumentoElectronico();
        relacion.setEventoInutilizacionDeId(eventoId);
        relacion.setEventoInutilizacionDeSucursalId(eventoSucursalId);
        relacion.setDocumentoElectronicoId(documentoId);
        relacion.setDocumentoElectronicoSucursalId(documentoSucursalId);

        EventoInutilizacionDEDocumentoElectronico saved = save(relacion);
        log.info("Relación creada entre evento {} y documento {}", eventoId, documentoId);
        return saved;
    }

    /**
     * Crear múltiples relaciones entre un evento y una lista de documentos electrónicos.
     */
    @Transactional
    public int crearRelaciones(
            Long eventoId,
            Long eventoSucursalId,
            List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos) {
        
        int creadas = 0;
        for (com.franco.dev.domain.financiero.DocumentoElectronico documento : documentos) {
            EventoInutilizacionDEDocumentoElectronico relacion = crearRelacion(
                    eventoId,
                    eventoSucursalId,
                    documento.getId(),
                    documento.getSucursalId()
            );
            if (relacion != null) {
                creadas++;
            }
        }
        log.info("Creadas {} relaciones para el evento {}", creadas, eventoId);
        return creadas;
    }
}

