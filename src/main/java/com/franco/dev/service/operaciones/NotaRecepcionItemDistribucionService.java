package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.empresarial.Sucursal;
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
     * Buscar distribuciones por sucursal de influencia
     */
    public List<NotaRecepcionItemDistribucion> findBySucursalInfluenciaId(Long sucursalId) {
        return repository.findBySucursalInfluenciaId(sucursalId);
    }

    /**
     * Buscar distribuciones por NotaRecepcionItem y sucursal de influencia específica
     */
    public List<NotaRecepcionItemDistribucion> findByNotaRecepcionItemIdAndSucursalInfluenciaId(Long notaRecepcionItemId, Long sucursalId) {
        return repository.findByNotaRecepcionItemIdAndSucursalInfluenciaId(notaRecepcionItemId, sucursalId);
    }

    /**
     * Obtener cantidad distribuida para un NotaRecepcionItem en una sucursal de influencia específica
     */
    public Double getDistributedQuantityByNotaRecepcionItemIdAndSucursalInfluenciaId(Long notaRecepcionItemId, Long sucursalId) {
        return repository.getDistributedQuantityByNotaRecepcionItemIdAndSucursalInfluenciaId(notaRecepcionItemId, sucursalId);
    }

    /**
     * Buscar distribuciones por pedido ID
     */
    public List<NotaRecepcionItemDistribucion> findDistribucionesByPedidoId(Long pedidoId) {
        System.out.println("=== INICIO findDistribucionesByPedidoId ===");
        System.out.println("Buscando distribuciones para pedido ID: " + pedidoId);
        
        try {
            List<NotaRecepcionItemDistribucion> result = repository.findByPedidoId(pedidoId);
            System.out.println("Distribuciones encontradas en servicio: " + result.size());
            System.out.println("=== FIN findDistribucionesByPedidoId ===");
            return result;
        } catch (Exception e) {
            System.err.println("=== ERROR en findDistribucionesByPedidoId ===");
            System.err.println("Error en servicio: " + e.getMessage());
            System.err.println("Tipo de excepción: " + e.getClass().getSimpleName());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Obtener sucursales únicas de entrega por pedido ID usando SQL nativo
     */
    public List<Sucursal> findSucursalesUnicasByPedidoId(Long pedidoId) {
        System.out.println("=== INICIO findSucursalesUnicasByPedidoId ===");
        System.out.println("Buscando sucursales únicas para pedido ID: " + pedidoId);
        
        try {
            List<Sucursal> result = repository.findSucursalesUnicasByPedidoId(pedidoId);
            System.out.println("Sucursales únicas encontradas: " + result.size());
            for (Sucursal sucursal : result) {
                System.out.println("  - " + sucursal.getNombre() + " (ID: " + sucursal.getId() + ")");
            }
            System.out.println("=== FIN findSucursalesUnicasByPedidoId ===");
            return result;
        } catch (Exception e) {
            System.err.println("=== ERROR en findSucursalesUnicasByPedidoId ===");
            System.err.println("Error en servicio: " + e.getMessage());
            System.err.println("Tipo de excepción: " + e.getClass().getSimpleName());
            e.printStackTrace();
            throw e;
        }
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

    /**
     * Verifica si la distribución de un nota recepcion item está concluida
     * @param notaRecepcionItemId ID del nota recepcion item
     * @param cantidadEnNota Cantidad en nota del item (en unidades base)
     * @return true si la distribución está concluida, false si está pendiente
     */
    public boolean isDistribucionConcluida(Long notaRecepcionItemId, Double cantidadEnNota) {
        if (notaRecepcionItemId == null || cantidadEnNota == null || cantidadEnNota <= 0) {
            return false;
        }

        // Obtener la suma total de cantidades distribuidas para este nota recepcion item
        Double totalDistribuido = repository.getTotalDistributedQuantityByNotaRecepcionItemId(notaRecepcionItemId);
        
        if (totalDistribuido == null) {
            totalDistribuido = 0.0;
        }

        // La distribución está concluida si la cantidad distribuida es igual a la cantidad en nota
        // Usamos una tolerancia pequeña para manejar errores de punto flotante
        double tolerancia = 0.001;
        return Math.abs(totalDistribuido - cantidadEnNota) <= tolerancia;
    }
} 