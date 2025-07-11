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

    @Query("SELECT rm FROM RecepcionMercaderia rm " +
           "WHERE (:proveedorId IS NULL OR rm.proveedor.id = :proveedorId) " +
           "AND (:sucursalId IS NULL OR rm.sucursalRecepcion.id = :sucursalId) " +
           "AND (:estado IS NULL OR rm.estado = :estado) " +
           "AND (:fechaInicio IS NULL OR rm.fecha >= :fechaInicio) " +
           "AND (:fechaFin IS NULL OR rm.fecha <= :fechaFin) " +
           "ORDER BY rm.fecha DESC")
    Page<RecepcionMercaderia> findByFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalId") Long sucursalId,
            @Param("estado") RecepcionMercaderiaEstado estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );

    @Query("SELECT rm FROM RecepcionMercaderia rm WHERE rm.proveedor.id = :proveedorId ORDER BY rm.fecha DESC")
    List<RecepcionMercaderia> findByProveedorId(@Param("proveedorId") Long proveedorId);

    @Query("SELECT rm FROM RecepcionMercaderia rm WHERE rm.estado = :estado")
    List<RecepcionMercaderia> findByEstado(@Param("estado") RecepcionMercaderiaEstado estado);
} 