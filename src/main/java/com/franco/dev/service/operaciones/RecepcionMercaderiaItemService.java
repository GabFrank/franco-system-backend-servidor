package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RecepcionMercaderiaItemService extends CrudService<RecepcionMercaderiaItem, RecepcionMercaderiaItemRepository, Long> {
    
    private final RecepcionMercaderiaItemRepository repository;

    @Override
    public RecepcionMercaderiaItemRepository getRepository() {
        return repository;
    }

    /**
     * Busca ítems por ID de recepción de mercadería
     */
    public List<RecepcionMercaderiaItem> findByRecepcionMercaderiaId(Long recepcionId) {
        return repository.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Busca ítems por ID de ítem de nota de recepción
     */
    public List<RecepcionMercaderiaItem> findByNotaRecepcionItemId(Long notaRecepcionItemId) {
        return repository.findByNotaRecepcionItemId(notaRecepcionItemId);
    }

    /**
     * Busca ítems por producto y sucursal de entrega
     */
    public List<RecepcionMercaderiaItem> findByProductoIdAndSucursalEntregaId(Long productoId, Long sucursalId) {
        return repository.findByProductoIdAndSucursalEntregaId(productoId, sucursalId);
    }

    /**
     * Busca ítems por lote y producto (para trazabilidad)
     */
    public List<RecepcionMercaderiaItem> findByLoteAndProductoId(String lote, Long productoId) {
        return repository.findByLoteAndProductoId(lote, productoId);
    }
} 