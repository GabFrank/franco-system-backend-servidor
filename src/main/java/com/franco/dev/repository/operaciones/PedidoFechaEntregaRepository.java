package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PedidoFechaEntrega;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PedidoFechaEntregaRepository extends HelperRepository<PedidoFechaEntrega, Long> {
    default Class<PedidoFechaEntrega> getEntityClass() {
        return PedidoFechaEntrega.class;
    }

    List<PedidoFechaEntrega> findByPedidoIdOrderByFechaEntregaDesc(Long id);
}