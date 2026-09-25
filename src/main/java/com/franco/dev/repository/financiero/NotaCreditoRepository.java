package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotaCreditoRepository extends HelperRepository<NotaCredito, EmbebedPrimaryKey> {

    default Class<NotaCredito> getEntityClass() {
        return NotaCredito.class;
    }

    /** Siguiente id de la secuencia: la PK compuesta impide usar @GeneratedValue. */
    @Query(value = "SELECT nextval('financiero.nota_credito_id_seq')", nativeQuery = true)
    Long siguienteId();

    @Query("SELECT n FROM NotaCredito n WHERE n.id = :id AND n.sucursalId = :sucursalId")
    Optional<NotaCredito> findByIdAndSucursalId(@Param("id") Long id, @Param("sucursalId") Long sucursalId);

    @Query("SELECT COALESCE(MAX(n.numeroNotaCredito), 0) FROM NotaCredito n "
            + "WHERE n.timbradoDetalleId = :timbradoDetalleId")
    Integer findMaxNumeroByTimbradoDetalleId(@Param("timbradoDetalleId") Long timbradoDetalleId);

    /** Notas activas de una factura: una factura puede tener varias, pero no dos totales. */
    @Query("SELECT n FROM NotaCredito n WHERE n.facturaLegalId = :facturaLegalId "
            + "AND n.sucursalId = :sucursalId AND n.activo = true ORDER BY n.id ASC")
    List<NotaCredito> findActivasByFactura(@Param("facturaLegalId") Long facturaLegalId,
                                           @Param("sucursalId") Long sucursalId);

    @Query("SELECT n FROM NotaCredito n WHERE "
            + "(:sucursalId IS NULL OR n.sucursalId = :sucursalId) AND "
            + "(cast(:fechaInicio as timestamp) IS NULL OR n.fecha >= :fechaInicio) AND "
            + "(cast(:fechaFin as timestamp) IS NULL OR n.fecha <= :fechaFin) AND "
            + "n.activo = true "
            + "ORDER BY n.fecha DESC, n.id DESC")
    Page<NotaCredito> findByFilters(@Param("sucursalId") Long sucursalId,
                                    @Param("fechaInicio") LocalDateTime fechaInicio,
                                    @Param("fechaFin") LocalDateTime fechaFin,
                                    Pageable pageable);
}
