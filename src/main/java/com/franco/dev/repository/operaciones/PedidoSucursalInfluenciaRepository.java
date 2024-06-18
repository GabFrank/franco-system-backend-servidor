package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface PedidoSucursalInfluenciaRepository extends HelperRepository<PedidoSucursalInfluencia, Long> {
    default Class<PedidoSucursalInfluencia> getEntityClass() {
        return PedidoSucursalInfluencia.class;
    }

    List<PedidoSucursalInfluencia> findByPedidoIdOrderBySucursalIdAsc(Long id);

}