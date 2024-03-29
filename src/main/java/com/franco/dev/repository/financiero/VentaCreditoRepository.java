package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaCreditoRepository extends HelperRepository<VentaCredito, EmbebedPrimaryKey> {

    default Class<VentaCredito> getEntityClass() {
        return VentaCredito.class;
    }

    public List<VentaCredito> findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(Long id, LocalDateTime start, LocalDateTime end);

    public List<VentaCredito> findAllByClienteIdAndEstadoOrderByCreadoEnDesc(Long id, EstadoVentaCredito estado, Pageable pageable);

    public List<VentaCredito> findAllByClienteIdAndEstadoOrderByCreadoEnDesc(Long id, EstadoVentaCredito estado);

    public long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado);

//    Moneda findByPaisId(Long id);

}