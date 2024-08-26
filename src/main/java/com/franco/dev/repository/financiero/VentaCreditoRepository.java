package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaCreditoRepository extends HelperRepository<VentaCredito, EmbebedPrimaryKey> {

    default Class<VentaCredito> getEntityClass() {
        return VentaCredito.class;
    }

    public List<VentaCredito> findAllByClienteIdAndCreadoEnLessThanEqualAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(Long id, LocalDateTime start, LocalDateTime end);

    public Page<VentaCredito> findAllByClienteIdAndEstadoOrderByCreadoEnDesc(Long id, EstadoVentaCredito estado, Pageable pageable);

    @Query(value = "SELECT vc FROM VentaCredito vc " +
            "WHERE vc.cliente.id = (:clienteId) " +
            "AND (vc.estado = (:estado) or cast(:estado as com.franco.dev.domain.financiero.enums.EstadoVentaCredito) IS NULL) " +
            "AND (:cobro = true or (vc.creadoEn between cast(:fechaInicio as timestamp) and cast(:fechaFin as timestamp))) " +
            "AND (:cobro = false or (vc.fechaCobro between cast(:fechaInicio as timestamp) and cast(:fechaFin as timestamp))) " +
            "ORDER BY vc.creadoEn DESC")
    List<VentaCredito> findAllWithDateAndFilters(
            @Param("clienteId") Long clienteId,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            @Param("estado") EstadoVentaCredito estado,
            @Param("cobro") Boolean cobro
    );

    @Query(value = "SELECT vc FROM VentaCredito vc " +
            "WHERE vc.cliente.id = (:clienteId) " +
            "AND (vc.estado = (:estado) or cast(:estado as com.franco.dev.domain.financiero.enums.EstadoVentaCredito) IS NULL) " +
            "ORDER BY vc.creadoEn DESC")
    List<VentaCredito> findAllWithFilters(
            @Param("clienteId") Long clienteId,
            @Param("estado") EstadoVentaCredito estado
    );

    @Query(value = "SELECT sum(vc.id) FROM VentaCredito vc " +
            "WHERE vc.cliente.id = (:clienteId) " +
            "AND (vc.estado = (:estado) or cast(:estado as com.franco.dev.domain.financiero.enums.EstadoVentaCredito) IS NULL)")
    Long countWithFilters(
            @Param("clienteId") Long clienteId,
            @Param("estado") EstadoVentaCredito estado
    );

    public List<VentaCredito> findAllByClienteIdAndEstadoOrderByCreadoEnDesc(Long id, EstadoVentaCredito estado);

    public long countByClienteIdAndEstado(Long id, EstadoVentaCredito estado);

    public VentaCredito findByVentaIdAndSucursalId(Long id, Long sucId);

    public VentaCredito findByIdAndSucursalId(Long id, Long sucId);

//    Moneda findByPaisId(Long id);

}