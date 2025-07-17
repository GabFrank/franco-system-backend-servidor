package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.repository.operaciones.NotaRecepcionItemDistribucionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class NotaRecepcionItemDistribucionService extends CrudService<NotaRecepcionItemDistribucion, NotaRecepcionItemDistribucionRepository, Long> {
    
    private final NotaRecepcionItemDistribucionRepository repository;

    @Override
    public NotaRecepcionItemDistribucionRepository getRepository() {
        return repository;
    }

    @Override
    public NotaRecepcionItemDistribucion save(NotaRecepcionItemDistribucion entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    /**
     * Obtener distribuciones por NotaRecepcionItem
     */
    public List<NotaRecepcionItemDistribucion> findByNotaRecepcionItemId(Long notaRecepcionItemId) {
        return repository.findByNotaRecepcionItemId(notaRecepcionItemId);
    }

    /**
     * Obtener distribuciones por sucursal de entrega
     */
    public List<NotaRecepcionItemDistribucion> findBySucursalEntregaId(Long sucursalId) {
        return repository.findBySucursalEntregaId(sucursalId);
    }

    /**
     * Obtener distribuciones por NotaRecepcion
     */
    public List<NotaRecepcionItemDistribucion> findByNotaRecepcionId(Long notaRecepcionId) {
        return repository.findByNotaRecepcionId(notaRecepcionId);
    }

    /**
     * Obtener cantidad total distribuida para un NotaRecepcionItem
     */
    public Double getTotalDistributedQuantityByNotaRecepcionItemId(Long notaRecepcionItemId) {
        return repository.getTotalDistributedQuantityByNotaRecepcionItemId(notaRecepcionItemId);
    }

    /**
     * Obtener cantidad distribuida para un NotaRecepcionItem en una sucursal específica
     */
    public Double getDistributedQuantityByNotaRecepcionItemIdAndSucursalId(Long notaRecepcionItemId, Long sucursalId) {
        return repository.getDistributedQuantityByNotaRecepcionItemIdAndSucursalId(notaRecepcionItemId, sucursalId);
    }

    /**
     * Eliminar todas las distribuciones de un NotaRecepcionItem
     */
    @Transactional
    public void deleteByNotaRecepcionItemId(Long notaRecepcionItemId) {
        repository.deleteByNotaRecepcionItemId(notaRecepcionItemId);
    }

    /**
     * Buscar distribuciones por NotaRecepcionItem y sucursal específica
     */
    public List<NotaRecepcionItemDistribucion> findByNotaRecepcionItemIdAndSucursalEntregaId(Long notaRecepcionItemId, Long sucursalId) {
        return repository.findByNotaRecepcionItemIdAndSucursalEntregaId(notaRecepcionItemId, sucursalId);
    }

    /**
     * Guardar múltiples distribuciones para un NotaRecepcionItem
     */
    @Transactional
    public List<NotaRecepcionItemDistribucion> saveDistribuciones(List<NotaRecepcionItemDistribucion> distribuciones) {
        return distribuciones.stream()
                .map(this::save)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Reemplazar todas las distribuciones de un NotaRecepcionItem
     */
    @Transactional
    public List<NotaRecepcionItemDistribucion> replaceDistribuciones(Long notaRecepcionItemId, List<NotaRecepcionItemDistribucion> nuevasDistribuciones) {
        // Eliminar distribuciones existentes
        deleteByNotaRecepcionItemId(notaRecepcionItemId);
        
        // Guardar nuevas distribuciones
        return saveDistribuciones(nuevasDistribuciones);
    }
} 