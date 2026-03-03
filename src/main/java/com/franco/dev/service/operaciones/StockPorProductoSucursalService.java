package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.StockPorProductoSucursal;
import com.franco.dev.repository.operaciones.StockPorProductoSucursalRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Servicio para StockPorProductoSucursal
 * 
 * NOTA: Este servicio actualmente NO está en uso.
 * La entidad StockPorProductoSucursal no se actualiza ni se utiliza.
 * El stock se calcula dinámicamente desde MovimientoStock.
 * 
 * Fecha de desactivación: 2026-01-30
 */
@Service
@AllArgsConstructor
public class StockPorProductoSucursalService extends CrudService<StockPorProductoSucursal, StockPorProductoSucursalRepository, EmbebedPrimaryKey> {
    private final StockPorProductoSucursalRepository repository;

    @Override
    public StockPorProductoSucursalRepository getRepository() {
        return repository;
    }


}