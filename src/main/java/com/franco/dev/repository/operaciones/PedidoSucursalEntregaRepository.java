package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PedidoSucursalEntregaRepository extends HelperRepository<PedidoSucursalEntrega, Long> {
    default Class<PedidoSucursalEntrega> getEntityClass() {
        return PedidoSucursalEntrega.class;
    }

    List<PedidoSucursalEntrega> findByPedidoIdOrderBySucursalIdAsc(Long id);
}