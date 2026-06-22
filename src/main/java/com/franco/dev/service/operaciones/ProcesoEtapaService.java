package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.ProcesoEtapaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ProcesoEtapaService extends CrudService<ProcesoEtapa, ProcesoEtapaRepository, Long> {
    
    private final ProcesoEtapaRepository repository;

    @Override
    public ProcesoEtapaRepository getRepository() {
        return repository;
    }

    /**
     * Obtiene todas las etapas de un pedido ordenadas por tipo
     */
    public List<ProcesoEtapa> getEtapasByPedidoId(Long pedidoId) {
        return repository.findByPedidoIdOrderByTipoEtapa(pedidoId);
    }

    /**
     * Busca una etapa específica de un pedido
     */
    public Optional<ProcesoEtapa> getEtapaByPedidoAndTipo(Long pedidoId, ProcesoEtapaTipo tipo) {
        return repository.findByPedidoIdAndTipoEtapa(pedidoId, tipo);
    }

    /**
     * Inicia una nueva etapa para un pedido
     */
    @Transactional
    public ProcesoEtapa iniciarEtapa(Pedido pedido, ProcesoEtapaTipo tipo, Usuario usuario) {
        // Verificar si la etapa ya existe
        Optional<ProcesoEtapa> etapaExistente = getEtapaByPedidoAndTipo(pedido.getId(), tipo);
        if (etapaExistente.isPresent()) {
            throw new IllegalStateException("La etapa " + tipo + " ya existe para el pedido " + pedido.getId());
        }

        ProcesoEtapa nuevaEtapa = new ProcesoEtapa();
        nuevaEtapa.setPedido(pedido);
        nuevaEtapa.setTipoEtapa(tipo);
        nuevaEtapa.setEstadoEtapa(ProcesoEtapaEstado.EN_PROCESO);
        nuevaEtapa.setFechaInicio(LocalDateTime.now());
        nuevaEtapa.setUsuarioInicio(usuario);
        nuevaEtapa.setCreadoEn(LocalDateTime.now());

        return save(nuevaEtapa);
    }

    /**
     * Finaliza una etapa existente
     */
    @Transactional
    public ProcesoEtapa finalizarEtapa(Long pedidoId, ProcesoEtapaTipo tipo) {
        Optional<ProcesoEtapa> etapaOpt = getEtapaByPedidoAndTipo(pedidoId, tipo);
        if (!etapaOpt.isPresent()) {
            throw new IllegalStateException("No se encontró la etapa " + tipo + " para el pedido " + pedidoId);
        }

        ProcesoEtapa etapa = etapaOpt.get();
        if (etapa.getEstadoEtapa() == ProcesoEtapaEstado.COMPLETADA) {
            throw new IllegalStateException("La etapa " + tipo + " ya está completada");
        }

        etapa.setEstadoEtapa(ProcesoEtapaEstado.COMPLETADA);
        etapa.setFechaFin(LocalDateTime.now());

        return save(etapa);
    }

    /**
     * Omite una etapa (la marca como omitida)
     */
    @Transactional
    public ProcesoEtapa omitirEtapa(Long pedidoId, ProcesoEtapaTipo tipo) {
        Optional<ProcesoEtapa> etapaOpt = getEtapaByPedidoAndTipo(pedidoId, tipo);
        if (!etapaOpt.isPresent()) {
            throw new IllegalStateException("No se encontró la etapa " + tipo + " para el pedido " + pedidoId);
        }

        ProcesoEtapa etapa = etapaOpt.get();
        etapa.setEstadoEtapa(ProcesoEtapaEstado.OMITIDA);
        etapa.setFechaFin(LocalDateTime.now());

        return save(etapa);
    }

    /**
     * Actualiza una etapa existente a estado EN_PROCESO
     * Usado cuando se inicia el trabajo en una etapa que ya existe
     */
    @Transactional
    public ProcesoEtapa actualizarEtapaAEnProceso(Long pedidoId, ProcesoEtapaTipo tipo) {
        Optional<ProcesoEtapa> etapaOpt = getEtapaByPedidoAndTipo(pedidoId, tipo);
        if (!etapaOpt.isPresent()) {
            throw new IllegalStateException("No se encontró la etapa " + tipo + " para el pedido " + pedidoId);
        }

        ProcesoEtapa etapa = etapaOpt.get();
        
        // Solo actualizar si está en estado PENDIENTE
        if (etapa.getEstadoEtapa() == ProcesoEtapaEstado.PENDIENTE) {
            etapa.setEstadoEtapa(ProcesoEtapaEstado.EN_PROCESO);
            etapa.setFechaInicio(LocalDateTime.now());
            return save(etapa);
        }
        
        // Si ya está en proceso o completada, no hacer nada
        return etapa;
    }

    /**
     * Revierte la etapa CREACION de COMPLETADA a EN_PROCESO
     * Solo se permite si RECEPCION_NOTA está en estado PENDIENTE (no ha empezado)
     * 
     * @param pedidoId ID del pedido
     * @return La etapa CREACION revertida
     * @throws IllegalStateException Si CREACION no está COMPLETADA o RECEPCION_NOTA ya empezó
     */
    @Transactional
    public ProcesoEtapa revertirEtapaCreacion(Long pedidoId) {
        // Verificar que la etapa CREACION existe y está COMPLETADA
        Optional<ProcesoEtapa> etapaCreacionOpt = getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.CREACION);
        if (!etapaCreacionOpt.isPresent()) {
            throw new IllegalStateException("No se encontró la etapa CREACION para el pedido: " + pedidoId);
        }
        
        ProcesoEtapa etapaCreacion = etapaCreacionOpt.get();
        if (etapaCreacion.getEstadoEtapa() != ProcesoEtapaEstado.COMPLETADA) {
            throw new IllegalStateException("Solo se puede revertir la etapa CREACION si está COMPLETADA. Estado actual: " + etapaCreacion.getEstadoEtapa());
        }
        
        // Verificar que RECEPCION_NOTA está en estado PENDIENTE (no ha empezado)
        Optional<ProcesoEtapa> etapaRecepcionNotaOpt = getEtapaByPedidoAndTipo(pedidoId, ProcesoEtapaTipo.RECEPCION_NOTA);
        if (!etapaRecepcionNotaOpt.isPresent()) {
            throw new IllegalStateException("No se encontró la etapa RECEPCION_NOTA para el pedido: " + pedidoId);
        }
        
        ProcesoEtapa etapaRecepcionNota = etapaRecepcionNotaOpt.get();
        if (etapaRecepcionNota.getEstadoEtapa() != ProcesoEtapaEstado.PENDIENTE) {
            throw new IllegalStateException("Solo se puede revertir la planificación si RECEPCION_NOTA está PENDIENTE. Estado actual: " + etapaRecepcionNota.getEstadoEtapa());
        }
        
        // Revertir CREACION: cambiar de COMPLETADA a EN_PROCESO
        etapaCreacion.setEstadoEtapa(ProcesoEtapaEstado.EN_PROCESO);
        etapaCreacion.setFechaFin(null); // Eliminar fechaFin
        
        return save(etapaCreacion);
    }

    /**
     * Crea la etapa pendiente siguiente según el flujo del proceso
     */
    @Transactional
    public ProcesoEtapa crearEtapaSiguiente(Pedido pedido, ProcesoEtapaTipo tipoActual) {
        ProcesoEtapaTipo tipoSiguiente = getSiguienteEtapa(tipoActual);
        if (tipoSiguiente == null) {
            return null; // No hay más etapas
        }

        // Verificar que no exista ya
        Optional<ProcesoEtapa> etapaExistente = getEtapaByPedidoAndTipo(pedido.getId(), tipoSiguiente);
        if (etapaExistente.isPresent()) {
            return etapaExistente.get();
        }

        ProcesoEtapa siguienteEtapa = new ProcesoEtapa();
        siguienteEtapa.setPedido(pedido);
        siguienteEtapa.setTipoEtapa(tipoSiguiente);
        siguienteEtapa.setEstadoEtapa(ProcesoEtapaEstado.PENDIENTE);
        siguienteEtapa.setCreadoEn(LocalDateTime.now());

        return save(siguienteEtapa);
    }

    /**
     * Obtiene el tipo de etapa siguiente según el flujo del proceso
     */
    private ProcesoEtapaTipo getSiguienteEtapa(ProcesoEtapaTipo tipoActual) {
        switch (tipoActual) {
            case CREACION:
                return ProcesoEtapaTipo.RECEPCION_NOTA;
            case RECEPCION_NOTA:
                return ProcesoEtapaTipo.RECEPCION_MERCADERIA;
            case RECEPCION_MERCADERIA:
                return ProcesoEtapaTipo.SOLICITUD_PAGO;
            case SOLICITUD_PAGO:
                return null; // Última etapa - las solicitudes de pago son independientes
            default:
                throw new IllegalArgumentException("Tipo de etapa no reconocido: " + tipoActual);
        }
    }

    /**
     * Verifica si una etapa está completada
     */
    public boolean isEtapaCompletada(Long pedidoId, ProcesoEtapaTipo tipo) {
        Optional<ProcesoEtapa> etapaOpt = getEtapaByPedidoAndTipo(pedidoId, tipo);
        return etapaOpt.isPresent() && 
               etapaOpt.get().getEstadoEtapa() == ProcesoEtapaEstado.COMPLETADA;
    }

    /**
     * Cancela todas las etapas de un pedido marcándolas como CANCELADA.
     */
    @Transactional
    public void cancelarEtapasByPedidoId(Long pedidoId) {
        List<ProcesoEtapa> etapas = getEtapasByPedidoId(pedidoId);
        if (etapas.isEmpty()) {
            throw new IllegalStateException("No se encontraron etapas para el pedido: " + pedidoId);
        }

        boolean yaCancelado = etapas.stream()
                .allMatch(e -> e.getEstadoEtapa() == ProcesoEtapaEstado.CANCELADA);
        if (yaCancelado) {
            throw new IllegalStateException("El pedido ya está cancelado");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ProcesoEtapa etapa : etapas) {
            if (etapa.getEstadoEtapa() != ProcesoEtapaEstado.CANCELADA) {
                etapa.setEstadoEtapa(ProcesoEtapaEstado.CANCELADA);
                etapa.setFechaFin(now);
                save(etapa);
            }
        }
    }

    public boolean isPedidoCancelado(Long pedidoId) {
        List<ProcesoEtapa> etapas = getEtapasByPedidoId(pedidoId);
        return !etapas.isEmpty() && etapas.stream()
                .allMatch(e -> e.getEstadoEtapa() == ProcesoEtapaEstado.CANCELADA);
    }

    /**
     * Obtiene el estado actual del proceso para un pedido
     */
    public ProcesoEtapaTipo getEtapaTipoActual(Long pedidoId) {
        List<ProcesoEtapa> etapas = getEtapasByPedidoId(pedidoId);

        if (isPedidoCancelado(pedidoId)) {
            return null;
        }
        
        // Buscar la última etapa en proceso o la primera pendiente
        for (ProcesoEtapa etapa : etapas) {
            if (etapa.getEstadoEtapa() == ProcesoEtapaEstado.EN_PROCESO) {
                return etapa.getTipoEtapa();
            }
        }
        
        // Si no hay ninguna en proceso, buscar la primera pendiente
        for (ProcesoEtapa etapa : etapas) {
            if (etapa.getEstadoEtapa() == ProcesoEtapaEstado.PENDIENTE) {
                return etapa.getTipoEtapa();
            }
        }
        
        // Si no hay pendientes ni en proceso, asumir que necesita creación
        return ProcesoEtapaTipo.CREACION;
    }

    // retorna la etapa actual del pedido, similar a getEtapaTipoActual pero retorna la etapa completa
    public ProcesoEtapa getEtapaActual(Long pedidoId) {
        List<ProcesoEtapa> etapas = getEtapasByPedidoId(pedidoId);

        if (isPedidoCancelado(pedidoId)) {
            return etapas.stream()
                    .filter(e -> e.getEstadoEtapa() == ProcesoEtapaEstado.CANCELADA)
                    .findFirst()
                    .orElse(null);
        }

        for (ProcesoEtapa etapa : etapas) {
            if (etapa.getEstadoEtapa() == ProcesoEtapaEstado.EN_PROCESO) {
                return etapa;
            }
        }

        // si no hay ninguna en proceso, buscar la primera pendiente
        for (ProcesoEtapa etapa : etapas) {
            if (etapa.getEstadoEtapa() == ProcesoEtapaEstado.PENDIENTE) {
                return etapa;
            }
        }

        return null;
    }
} 