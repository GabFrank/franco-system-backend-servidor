package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.repository.operaciones.PedidoItemDistribucionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PedidoItemDistribucionService extends CrudService<PedidoItemDistribucion, PedidoItemDistribucionRepository, Long> {
    
    private final PedidoItemDistribucionRepository repository;

    @Override
    public PedidoItemDistribucionRepository getRepository() {
        return repository;
    }

    /**
     * Busca distribuciones por ID de ítem de pedido
     */
    public List<PedidoItemDistribucion> findByPedidoItemId(Long pedidoItemId) {
        return repository.findByPedidoItemId(pedidoItemId);
    }

    /**
     * Busca distribuciones por ID de pedido
     */
    public List<PedidoItemDistribucion> findByPedidoId(Long pedidoId) {
        return repository.findByPedidoId(pedidoId);
    }

    /**
     * Busca distribuciones por sucursal de entrega
     */
    public List<PedidoItemDistribucion> findBySucursalEntregaId(Long sucursalId) {
        return repository.findBySucursalEntregaId(sucursalId);
    }

    /**
     * Busca distribuciones por sucursal de influencia
     */
    public List<PedidoItemDistribucion> findBySucursalInfluenciaId(Long sucursalId) {
        return repository.findBySucursalInfluenciaId(sucursalId);
    }

    /**
     * Elimina todas las distribuciones asociadas a un pedido item
     */
    public void deleteByPedidoItemId(Long pedidoItemId) {
        repository.deleteByPedidoItemId(pedidoItemId);
    }

    /**
     * Elimina múltiples distribuciones por IDs de forma robusta
     * Maneja casos donde algunas entidades ya no existen
     */
    public int deleteByIds(List<Long> ids) {
        int deletedCount = 0;
        for (Long id : ids) {
            try {
                if (repository.existsById(id)) {
                    repository.deleteById(id);
                    deletedCount++;
                }
            } catch (Exception e) {
                // Log error but continue with other deletions
                System.err.println("Error eliminando distribución con ID " + id + ": " + e.getMessage());
            }
        }
        return deletedCount;
    }

    /**
     * Verifica si la distribución de un pedido item está concluida
     * @param pedidoItemId ID del pedido item
     * @param cantidadSolicitada Cantidad solicitada del pedido item (en unidades base)
     * @return true si la distribución está concluida, false si está pendiente
     */
    public boolean isDistribucionConcluida(Long pedidoItemId, Double cantidadSolicitada) {
        if (pedidoItemId == null || cantidadSolicitada == null || cantidadSolicitada <= 0) {
            return false;
        }

        // Obtener la suma total de cantidades asignadas para este pedido item
        Double totalAsignado = repository.getTotalAsignadoByPedidoItemId(pedidoItemId);
        
        if (totalAsignado == null) {
            totalAsignado = 0.0;
        }

        // La distribución está concluida si la cantidad asignada es igual a la solicitada
        // Usamos una tolerancia pequeña para manejar errores de punto flotante
        double tolerancia = 0.001;
        return Math.abs(totalAsignado - cantidadSolicitada) <= tolerancia;
    }
} 