package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoDte;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.EventoDteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventoDteService {

    private final EventoDteRepository eventoDteRepository;

    // Constantes para tipos de evento
    public static final int TIPO_CANCELACION = 1;
    public static final int TIPO_RECHAZO = 2;
    public static final int TIPO_APROBACION = 3;
    public static final int TIPO_CORRECCION = 4;

    /**
     * Registra un evento de cancelación de DTE
     */
    public EventoDte registrarCancelacion(DocumentoElectronico documento, String motivo,
                                        Usuario usuario, String observacion) {
        log.info("[EVENTO-DTE] Registrando cancelación del documento {}, motivo: {}", documento.getId(), motivo);

        EventoDte evento = new EventoDte();
        evento.setDocumentoElectronico(documento);
        evento.setTipoEvento(TIPO_CANCELACION);
        evento.setMotivo(motivo);
        evento.setObservacion(observacion);
        evento.setUsuario(usuario);
        evento.setFechaEvento(LocalDateTime.now());
        evento.setCreadoEn(LocalDateTime.now());

        EventoDte eventoGuardado = eventoDteRepository.save(evento);
        log.info("[EVENTO-DTE] Evento de cancelación registrado exitosamente, ID: {}", eventoGuardado.getId());

        return eventoGuardado;
    }

    /**
     * Registra un evento genérico de DTE
     */
    public EventoDte registrarEvento(DocumentoElectronico documento, Integer tipoEvento,
                                   String motivo, String observacion, Usuario usuario) {
        log.debug("[EVENTO-DTE] Registrando evento tipo '{}' para documento {}", tipoEvento, documento.getId());

        EventoDte evento = new EventoDte();
        evento.setDocumentoElectronico(documento);
        evento.setTipoEvento(tipoEvento);
        evento.setMotivo(motivo);
        evento.setObservacion(observacion);
        evento.setUsuario(usuario);
        evento.setFechaEvento(LocalDateTime.now());
        evento.setCreadoEn(LocalDateTime.now());

        EventoDte eventoGuardado = eventoDteRepository.save(evento);
        log.debug("[EVENTO-DTE] Evento tipo '{}' registrado exitosamente, ID: {}", tipoEvento, eventoGuardado.getId());

        return eventoGuardado;
    }

    /**
     * Actualiza la respuesta de SIFEN en un evento
     */
    public void actualizarRespuestaSifen(Long eventoId, String cdcEvento, String mensajeRespuestaSifen) {
        EventoDte evento = eventoDteRepository.findById(eventoId).orElse(null);
        if (evento != null) {
            evento.setCdcEvento(cdcEvento);
            evento.setMensajeRespuestaSifen(mensajeRespuestaSifen);
            eventoDteRepository.save(evento);
            log.debug("[EVENTO-DTE] Respuesta SIFEN actualizada para evento {}", eventoId);
        }
    }

    // Métodos de consulta
    public List<EventoDte> findByDocumentoElectronicoId(Long documentoId) {
        return eventoDteRepository.findByDocumentoElectronicoIdOrderByIdAsc(documentoId);
    }

    public EventoDte findById(Long id) {
        return eventoDteRepository.findById(id).orElse(null);
    }

    public List<EventoDte> findAll() {
        return eventoDteRepository.findAll();
    }

    public EventoDte save(EventoDte evento) {
        return eventoDteRepository.save(evento);
    }

    public void deleteById(Long id) {
        eventoDteRepository.deleteById(id);
    }

    /**
     * Obtiene el nombre descriptivo del tipo de evento
     */
    public String getNombreTipoEvento(Integer tipoEvento) {
        switch (tipoEvento) {
            case TIPO_CANCELACION:
                return "Cancelación";
            case TIPO_RECHAZO:
                return "Rechazo";
            case TIPO_APROBACION:
                return "Aprobación";
            case TIPO_CORRECCION:
                return "Corrección";
            default:
                return "Desconocido";
        }
    }
}
