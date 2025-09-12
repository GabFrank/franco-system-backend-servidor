package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.graphql.operaciones.dto.ResumenItemsNotaDTO;
import com.franco.dev.repository.operaciones.NotaRecepcionItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@AllArgsConstructor
public class NotaRecepcionItemService extends CrudService<NotaRecepcionItem, NotaRecepcionItemRepository, Long> {
    private final NotaRecepcionItemRepository repository;

    @Override
    public NotaRecepcionItemRepository getRepository() {
        return repository;
    }

    public List<NotaRecepcionItem> findByNotaRecepcionId(Long id) {
        return repository.findByNotaRecepcionId(id);
    }

    public List<NotaRecepcionItem> findByPedidoIdAndPedidoItemIsNull(Long id) {
        return repository.findByPedidoIdAndPedidoItemIsNull(id);
    }

    public List<NotaRecepcionItem> findByPedidoItemId(Long pedidoItemId) {
        return repository.findByPedidoItemId(pedidoItemId);
    }

    public Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIds(Long notaRecepcionId, List<Long> sucursalesIds, String filtroTexto, Pageable pageable) {
        return repository.findByNotaRecepcionIdAndSucursalesIds(notaRecepcionId, sucursalesIds, filtroTexto, pageable);
    }

    /**
     * Busca ítems por nota de recepción y sucursales, excluyendo los que ya tienen recepción
     */
    public Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIdsAndNoVerificados(
            Long notaRecepcionId, List<Long> sucursalesIds, String filtroTexto, Pageable pageable) {
        return repository.findByNotaRecepcionIdAndSucursalesIdsAndNoVerificados(notaRecepcionId, sucursalesIds, filtroTexto, pageable);
    }

    /**
     * Busca ítems por nota de recepción y sucursales, solo los que ya tienen recepción
     */
    public Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIdsAndVerificados(
            Long notaRecepcionId, List<Long> sucursalesIds, String filtroTexto, Pageable pageable) {
        return repository.findByNotaRecepcionIdAndSucursalesIdsAndVerificados(notaRecepcionId, sucursalesIds, filtroTexto, pageable);
    }

    /**
     * Busca ítems por nota de recepción y sucursales, solo los que tienen rechazos
     */
    public Page<NotaRecepcionItem> findByNotaRecepcionIdAndSucursalesIdsAndRechazados(
            Long notaRecepcionId, List<Long> sucursalesIds, String filtroTexto, Pageable pageable) {
        return repository.findByNotaRecepcionIdAndSucursalesIdsAndRechazados(notaRecepcionId, sucursalesIds, filtroTexto, pageable);
    }

    @Override
    public NotaRecepcionItem save(NotaRecepcionItem entity) {
        NotaRecepcionItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }

    /**
     * Guarda un NotaRecepcionItem asegurando que la presentación se mapee correctamente
     * Este método es usado específicamente por NotaRecepcionService.asignarItemsANota()
     */
    @Transactional
    public NotaRecepcionItem saveWithPresentacionMapping(NotaRecepcionItem entity) {
        // Asegurar que la presentación se mapee correctamente antes de guardar
        if (entity.getPresentacionEnNota() != null) {
            // La presentación ya está mapeada correctamente desde el NotaRecepcionService
            // Solo necesitamos guardar la entidad
            return super.save(entity);
        } else {
            // Si no hay presentación, guardar sin ella (caso edge)
            return super.save(entity);
        }
    }

    /**
     * Calcula la cantidad pendiente de recibir físicamente para un NotaRecepcionItem
     * cantidadPendiente = cantidadEnNota - sum(cantidadRecibida) de todos los RecepcionMercaderiaItem asociados
     * @param notaRecepcionItemId ID del nota recepcion item
     * @return Cantidad pendiente (cantidadEnNota - sum(cantidadRecibida))
     */
    public Double getCantidadPendiente(Long notaRecepcionItemId) {
        if (notaRecepcionItemId == null) {
            return 0.0;
        }
        
        Double cantidadPendiente = repository.getCantidadPendienteByNotaRecepcionItemId(notaRecepcionItemId);
        return cantidadPendiente != null ? Math.max(0.0, cantidadPendiente) : 0.0;
    }

    /**
     * Obtiene items de una nota de recepción de forma paginada
     */
    public Page<NotaRecepcionItem> findItemsByNotaIdPaginados(Long notaId, Pageable pageable) {
        return repository.findItemsByNotaIdPaginados(notaId, pageable);
    }

    /**
     * Obtiene resumen de items por estado para múltiples notas
     */
    public List<ResumenItemsNotaDTO> getResumenItemsPorNota(List<Long> notaIds) {
        return repository.getResumenItemsPorNota(notaIds);
    }

    /**
     * Obtiene resumen general de items para múltiples notas
     */
    public Object[] getResumenGeneralItems(List<Long> notaIds) {
        return repository.getResumenGeneralItems(notaIds);
    }
}