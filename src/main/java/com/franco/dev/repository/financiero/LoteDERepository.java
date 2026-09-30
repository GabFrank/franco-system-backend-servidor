package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.repository.HelperRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoteDERepository extends HelperRepository<LoteDE, EmbebedPrimaryKey> {

    default Class<LoteDE> getEntityClass() {
        return LoteDE.class;
    }

    List<LoteDE> findAllByOrderByIdAsc(Pageable pageable);

    @Query("SELECT l FROM LoteDE l WHERE " +
           "(l.estado = :estado OR cast(:estado as com.franco.dev.domain.financiero.enums.EstadoLoteDE) IS NULL) AND " +
           "(l.fechaProcesado >= :fechaInicio OR cast(:fechaInicio as timestamp) IS NULL) AND " +
           "(l.fechaProcesado <= :fechaFin OR cast(:fechaFin as timestamp) IS NULL) " +
           "ORDER BY l.id ASC")
    Page<LoteDE> findByEstadoOrFechaProcesadoBetween(
        @Param("estado") EstadoLoteDE estado,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    @Query("SELECT l FROM LoteDE l WHERE " +
           "l.estado IN ('PROCESADO', 'PROCESADO_CON_ERRORES', 'ERROR_PERMANENTE', 'RECHAZADO') AND " +
           "l.creadoEn < :fechaLimite " +
           "ORDER BY l.creadoEn ASC")
    List<LoteDE> findLotesAntiguosProcesados(@Param("fechaLimite") LocalDateTime fechaLimite);

    List<LoteDE> findByEstado(EstadoLoteDE estado);

    /** Siguiente id de la secuencia (impares en central desde V226.1). */
    @Query(value = "SELECT nextval('financiero.lote_de_id_seq')", nativeQuery = true)
    Long siguienteId();

    /** Lotes atrasados: los que quedaron sin enviarse (PENDIENTE_ENVIO, ERROR_ENVIO, ERROR_RED). */
    List<LoteDE> findByEstadoInOrderByCreadoEnAsc(List<EstadoLoteDE> estados);

    Optional<LoteDE> findByProtocolo(String protocolo);

    /**
     * Lotes EN_PROCESO de notas de credito o de remision: los unicos que el central envia por su
     * cuenta. Los de facturas llegan replicados de las filiales y los consulta cada filial; por eso
     * se filtra por las columnas de nota del DE, y por sucursal porque la PK es (id, sucursal_id).
     */
    @Query("SELECT l FROM LoteDE l WHERE l.estado = 'EN_PROCESO' AND EXISTS (" +
           "SELECT d FROM DocumentoElectronico d WHERE d.loteDeId = l.id AND d.sucursalId = l.sucursalId " +
           "AND (d.notaCreditoId IS NOT NULL OR d.notaRemisionId IS NOT NULL)) " +
           "ORDER BY l.creadoEn ASC")
    List<LoteDE> findEnProcesoDeNotas();

    @Query("SELECT l FROM LoteDE l WHERE l.id = :id AND l.sucursalId = :sucursalId")
    Optional<LoteDE> findByIdAndSucursalId(@Param("id") Long id, @Param("sucursalId") Long sucursalId);

}


 