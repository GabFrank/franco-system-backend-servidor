package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RecepcionMercaderiaRepository extends HelperRepository<RecepcionMercaderia, Long> {
    
    default Class<RecepcionMercaderia> getEntityClass() {
        return RecepcionMercaderia.class;
    }

    /*
    @Query("SELECT rm FROM RecepcionMercaderia rm " +
           "WHERE (:proveedorId IS NULL OR rm.proveedor.id = :proveedorId) " +
           "AND (:sucursalId IS NULL OR rm.sucursalRecepcion.id = :sucursalId) " +
           "AND (:estado IS NULL OR rm.estado = :estado) " +
           "AND rm.estado IN :estados " +
           "AND (:usuarioId IS NULL OR rm.usuario.id = :usuarioId) " +
           "AND (:fechaInicio IS NULL OR rm.fecha >= :fechaInicio) " +
           "AND (:fechaFin IS NULL OR rm.fecha <= :fechaFin) " +
           "ORDER BY rm.fecha DESC")
    Page<RecepcionMercaderia> findByFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalId") Long sucursalId,
            @Param("estado") RecepcionMercaderiaEstado estado,
            @Param("estados") List<RecepcionMercaderiaEstado> estados,
            @Param("usuarioId") Long usuarioId,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );
    */

    @Query("SELECT rm FROM RecepcionMercaderia rm WHERE rm.proveedor.id = :proveedorId ORDER BY rm.fecha DESC")
    List<RecepcionMercaderia> findByProveedorId(@Param("proveedorId") Long proveedorId);

    @Query("SELECT rm FROM RecepcionMercaderia rm WHERE rm.estado = :estado")
    List<RecepcionMercaderia> findByEstado(@Param("estado") RecepcionMercaderiaEstado estado);

    /**
     * Busca recepciones reutilizables por criterios específicos
     * Regla: Mismo proveedor, misma sucursal, misma fecha, estado EN_PROCESO/PENDIENTE
     */
    @Query("SELECT rm FROM RecepcionMercaderia rm " +
           "WHERE rm.proveedor.id = :proveedorId " +
           "AND rm.sucursalRecepcion.id = :sucursalRecepcionId " +
           "AND DATE(rm.fecha) = DATE(:fecha) " +
           "AND rm.estado IN ('EN_PROCESO', 'PENDIENTE') " +
           "AND (:usuarioId IS NULL OR rm.usuario.id = :usuarioId) " +
           "ORDER BY rm.fecha DESC")
    List<RecepcionMercaderia> findRecepcionesReutilizables(
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalRecepcionId") Long sucursalRecepcionId,
            @Param("fecha") LocalDateTime fecha,
            @Param("usuarioId") Long usuarioId);

    /**
     * Verifica si existe una recepción para un ítem de nota y sucursal específica
     */
    @Query("SELECT COUNT(rmi) > 0 FROM RecepcionMercaderiaItem rmi " +
           "WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId " +
           "AND rmi.sucursalEntrega.id = :sucursalEntregaId")
    boolean existeRecepcionParaNotaItemYSucursal(
            @Param("notaRecepcionItemId") Long notaRecepcionItemId,
            @Param("sucursalEntregaId") Long sucursalEntregaId);

    /**
     * Busca recepciones por pedidoId
     */
    @Query("SELECT DISTINCT rm FROM RecepcionMercaderia rm " +
           "JOIN RecepcionMercaderiaItem rmi ON rmi.recepcionMercaderia.id = rm.id " +
           "JOIN rmi.notaRecepcionItem nri " +
           "JOIN nri.notaRecepcion nr " +
           "WHERE nr.pedido.id = :pedidoId")
    List<RecepcionMercaderia> findByPedidoId(@Param("pedidoId") Long pedidoId);
} 