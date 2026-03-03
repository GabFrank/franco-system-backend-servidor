package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Devolucion;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DevolucionRepository extends HelperRepository<Devolucion, Long> {
    
    default Class<Devolucion> getEntityClass() {
        return Devolucion.class;
    }

    @Query("SELECT d FROM Devolucion d " +
           "WHERE (:proveedorId IS NULL OR d.proveedor.id = :proveedorId) " +
           "AND (:sucursalId IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "AND (:estado IS NULL OR d.estado = :estado) " +
           "AND (:fechaInicio IS NULL OR d.fecha >= :fechaInicio) " +
           "AND (:fechaFin IS NULL OR d.fecha <= :fechaFin) " +
           "ORDER BY d.fecha DESC")
    Page<Devolucion> findByFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("sucursalId") Long sucursalId,
            @Param("estado") DevolucionEstado estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );

    @Query("SELECT d FROM Devolucion d WHERE d.proveedor.id = :proveedorId ORDER BY d.fecha DESC")
    List<Devolucion> findByProveedorId(@Param("proveedorId") Long proveedorId);

    @Query("SELECT d FROM Devolucion d WHERE d.estado = :estado")
    List<Devolucion> findByEstado(@Param("estado") DevolucionEstado estado);
} 