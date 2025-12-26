package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.repository.operaciones.DevolucionItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class DevolucionItemService extends CrudService<DevolucionItem, DevolucionItemRepository, Long> {
    
    private final DevolucionItemRepository repository;

    @Override
    public DevolucionItemRepository getRepository() {
        return repository;
    }

    /**
     * Busca ítems por ID de devolución
     */
    public List<DevolucionItem> findByDevolucionId(Long devolucionId) {
        return repository.findByDevolucionId(devolucionId);
    }

    /**
     * Busca ítems por ID de producto
     */
    public List<DevolucionItem> findByProductoId(Long productoId) {
        return repository.findByProductoId(productoId);
    }

    /**
     * Busca ítems por ID de ítem de recepción de mercadería (para trazabilidad)
     */
    public List<DevolucionItem> findByRecepcionMercaderiaItemId(Long recepcionItemId) {
        return repository.findByRecepcionMercaderiaItemId(recepcionItemId);
    }
} 