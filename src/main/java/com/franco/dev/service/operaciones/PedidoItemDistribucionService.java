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
} 