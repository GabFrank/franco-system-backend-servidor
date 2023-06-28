package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCreditoCuota;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VentaCreditoCuotaRepository extends HelperRepository<VentaCreditoCuota, EmbebedPrimaryKey> {

    default Class<VentaCreditoCuota> getEntityClass() {
        return VentaCreditoCuota.class;
    }

    public List<VentaCreditoCuota> findAllByVentaCreditoIdAndSucursalId(Long id, Long sucId);

//    Moneda findByPaisId(Long id);

}