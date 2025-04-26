package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.StockPorProductoSucursal;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface StockPorProductoSucursalRepository extends HelperRepository<StockPorProductoSucursal, EmbebedPrimaryKey> {
    default Class<StockPorProductoSucursal> getEntityClass() {
        return StockPorProductoSucursal.class;
    }

    public StockPorProductoSucursal findByIdAndSucursalId(Long id, Long sucId);

    public List<StockPorProductoSucursal> findById(Long id);
}