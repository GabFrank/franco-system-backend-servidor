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

    // Nota: cada parametro nullable se envuelve con cast(:param as <tipo>) en el IS NULL.
    // Sin el cast, PostgreSQL no puede inferir el tipo del bind cuando llega null
    // ("no se pudo determinar el tipo de dato del parametro") y la query revienta.
    // Mismo idiom usado en FuncionarioRepository / EventoInutilizacionDERepository.
    @Query("SELECT d FROM Devolucion d " +
           "WHERE (cast(:proveedorId as long) IS NULL OR d.proveedor.id = :proveedorId) " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "AND (cast(:estado as com.franco.dev.domain.operaciones.enums.DevolucionEstado) IS NULL OR d.estado = :estado) " +
           "AND (cast(:fechaInicio as timestamp) IS NULL OR d.fecha >= :fechaInicio) " +
           "AND (cast(:fechaFin as timestamp) IS NULL OR d.fecha <= :fechaFin) " +
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

    @Query("SELECT d FROM Devolucion d WHERE d.proveedor.id = :proveedorId " +
           "AND d.estado IN :estados ORDER BY d.fecha DESC")
    List<Devolucion> findByProveedorIdAndEstados(@Param("proveedorId") Long proveedorId,
                                                 @Param("estados") List<DevolucionEstado> estados);

    /**
     * Devoluciones de un proveedor en un estado dado. Ambos parametros no-null.
     */
    @Query("SELECT d FROM Devolucion d WHERE d.proveedor.id = :proveedorId " +
           "AND d.estado = :estado ORDER BY d.sucursalOrigen.id ASC, d.fecha ASC")
    List<Devolucion> findByProveedorIdAndEstado(@Param("proveedorId") Long proveedorId,
                                                @Param("estado") DevolucionEstado estado);

    /**
     * Devoluciones de un proveedor en un estado dado, con sucursal opcional (nullable).
     * Usa el idiom cast(:param as tipo) en el IS NULL para que PostgreSQL pueda
     * determinar el tipo del parametro cuando llega null.
     */
    @Query("SELECT d FROM Devolucion d " +
           "WHERE d.proveedor.id = :proveedorId " +
           "AND d.estado = :estado " +
           "AND (cast(:sucursalId as long) IS NULL OR d.sucursalOrigen.id = :sucursalId) " +
           "ORDER BY d.sucursalOrigen.id ASC, d.fecha ASC")
    List<Devolucion> findByProveedorIdEstadoAndSucursal(@Param("proveedorId") Long proveedorId,
                                                        @Param("estado") DevolucionEstado estado,
                                                        @Param("sucursalId") Long sucursalId);
} 