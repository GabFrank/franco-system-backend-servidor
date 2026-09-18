package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotaRemisionRepository extends HelperRepository<NotaRemision, EmbebedPrimaryKey> {

    default Class<NotaRemision> getEntityClass() {
        return NotaRemision.class;
    }

    /** Siguiente id de la secuencia de la tabla: la PK compuesta impide usar @GeneratedValue. */
    @Query(value = "SELECT nextval('financiero.nota_remision_id_seq')", nativeQuery = true)
    Long siguienteId();

    @Query("SELECT n FROM NotaRemision n WHERE n.id = :id AND n.sucursalId = :sucursalId")
    Optional<NotaRemision> findByIdAndSucursalId(@Param("id") Long id, @Param("sucursalId") Long sucursalId);

    /**
     * Último número usado en la serie de este timbrado. Se llama con la fila del timbrado ya
     * bloqueada ({@link TimbradoDetalleRepository#lockById}), que es lo que evita que dos notas
     * simultáneas tomen el mismo número; la UNIQUE de la tabla es la red debajo.
     */
    @Query("SELECT COALESCE(MAX(n.numeroNotaRemision), 0) FROM NotaRemision n "
            + "WHERE n.timbradoDetalleId = :timbradoDetalleId")
    Integer findMaxNumeroByTimbradoDetalleId(@Param("timbradoDetalleId") Long timbradoDetalleId);

    @Query("SELECT n FROM NotaRemision n WHERE n.transferenciaId = :transferenciaId AND n.activo = true")
    List<NotaRemision> findActivasByTransferenciaId(@Param("transferenciaId") Long transferenciaId);

    /**
     * La que se expone por GraphQL. El id de transferencia es global, asi que sin el filtro de
     * sucursal cualquier usuario podia leer el chofer, el vehiculo y las direcciones de otra.
     */
    /** Para el guard de duplicados del origen FACTURA. Filtra por sucursal: la PK es compuesta. */
    @Query("SELECT n FROM NotaRemision n WHERE n.facturaLegalId = :facturaLegalId "
            + "AND n.sucursalId = :sucursalId AND n.activo = true")
    List<NotaRemision> findActivasByFacturaLegalId(@Param("facturaLegalId") Long facturaLegalId,
                                                   @Param("sucursalId") Long sucursalId);

    @Query("SELECT n FROM NotaRemision n WHERE n.transferenciaId = :transferenciaId "
            + "AND n.sucursalId = :sucursalId AND n.activo = true")
    List<NotaRemision> findActivasByTransferenciaIdAndSucursalId(
            @Param("transferenciaId") Long transferenciaId, @Param("sucursalId") Long sucursalId);

    @Query("SELECT n FROM NotaRemision n WHERE "
            + "(:sucursalId IS NULL OR n.sucursalId = :sucursalId) AND "
            + "(cast(:fechaInicio as timestamp) IS NULL OR n.fecha >= :fechaInicio) AND "
            + "(cast(:fechaFin as timestamp) IS NULL OR n.fecha <= :fechaFin) AND "
            + "n.activo = true "
            + "ORDER BY n.fecha DESC, n.id DESC")
    Page<NotaRemision> findByFilters(
            @Param("sucursalId") Long sucursalId,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable);
}
