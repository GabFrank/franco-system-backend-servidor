package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SolicitudPagoRepository extends HelperRepository<SolicitudPago, Long>, JpaSpecificationExecutor<SolicitudPago> {
    default Class<SolicitudPago> getEntityClass() {
        return SolicitudPago.class;
    }

    public List<SolicitudPago> findByUsuarioId(Long id);
    
    // public SolicitudPago findByTipoAndReferenciaId(TipoSolicitudPago tipo, Long referenciaId);
    
    public List<SolicitudPago> findByPagoId(Long pagoId);
    
    // Methods for solicitud pago functionality
    public List<SolicitudPago> findByEstado(SolicitudPagoEstado estado);
    
    public List<SolicitudPago> findByProveedorId(Long proveedorId);
    
    @Query("SELECT DISTINCT sp FROM SolicitudPago sp " +
           "JOIN sp.notasRecepcion spnr " +
           "JOIN spnr.notaRecepcion nr " +
           "WHERE nr.pedido.id = :pedidoId " +
           "ORDER BY sp.fechaSolicitud DESC")
    public List<SolicitudPago> findByPedidoId(@Param("pedidoId") Long pedidoId);
    
    @Query("SELECT DISTINCT sp FROM SolicitudPago sp " +
           "JOIN sp.notasRecepcion spnr " +
           "JOIN spnr.notaRecepcion nr " +
           "WHERE nr.pedido.id = :pedidoId " +
           "ORDER BY sp.fechaSolicitud DESC")
    public Page<SolicitudPago> findByPedidoIdPaginated(@Param("pedidoId") Long pedidoId, Pageable pageable);
    
    @Query("SELECT sp FROM SolicitudPago sp " +
           "WHERE sp.proveedor.id = :proveedorId " +
           "AND sp.estado = :estado " +
           "ORDER BY sp.fechaSolicitud DESC")
    public Page<SolicitudPago> findByProveedorIdAndEstado(@Param("proveedorId") Long proveedorId, 
                                                          @Param("estado") SolicitudPagoEstado estado, 
                                                          Pageable pageable);

    /**
     * Find SolicitudPago by ID with usuario and persona eagerly loaded for report printing.
     * Usado en: impresion PDF solicitud pago (evita LazyInitializationException en usuario)
     */
    @Query("SELECT DISTINCT sp FROM SolicitudPago sp " +
           "LEFT JOIN FETCH sp.usuario u " +
           "LEFT JOIN FETCH u.persona " +
           "LEFT JOIN FETCH sp.proveedor p " +
           "LEFT JOIN FETCH p.persona " +
           "LEFT JOIN FETCH sp.moneda " +
           "LEFT JOIN FETCH sp.formaPago " +
           "WHERE sp.id = :id")
    public java.util.Optional<SolicitudPago> findByIdWithUsuarioAndMoneda(@Param("id") Long id);

    // Using basic query for simple filters
    // Page<SolicitudPago> findByReferenciaIdAndTipoAndEstadoAndCreadoEnBetween(
    //         Long referenciaId, 
    //         TipoSolicitudPago tipo, 
    //         SolicitudPagoEstado estado, 
    //         LocalDateTime fechaInicio,
    //         LocalDateTime fechaFin,
    //         Pageable pageable);
}

