package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DocumentoElectronicoRepository extends HelperRepository<DocumentoElectronico, EmbebedPrimaryKey> {

    default Class<DocumentoElectronico> getEntityClass() {
        return DocumentoElectronico.class;
    }

    List<DocumentoElectronico> findAllByOrderByIdAsc(Pageable pageable);

    Optional<DocumentoElectronico> findByCdc(String cdc);
    
    @Query("SELECT d FROM DocumentoElectronico d " +
           "LEFT JOIN d.facturaLegal f " +
           "WHERE f.id = :facturaLegalId AND d.sucursalId = :sucursalId")
    Optional<DocumentoElectronico> findByFacturaLegalId(@Param("facturaLegalId") Long facturaLegalId, @Param("sucursalId") Long sucursalId);

    List<DocumentoElectronico> findByEstado(EstadoDE estado);

    @Query("SELECT d FROM DocumentoElectronico d " +
           "LEFT JOIN d.loteDe l " +
           "WHERE (:loteId IS NULL OR l.id = :loteId) AND " +
           "(:sucursalId IS NULL OR d.sucursalId = :sucursalId) AND " +
           "(:estado IS NULL OR d.estado = :estado)")
    List<DocumentoElectronico> findByLoteDeIdAndSucursalIdAndEstado(
        @Param("loteId") Long loteId, 
        @Param("sucursalId") Long sucursalId, 
        @Param("estado") EstadoDE estado);

    @Query("SELECT d FROM DocumentoElectronico d WHERE " +
           "(d.estado = :estado OR cast(:estado as com.franco.dev.domain.financiero.enums.EstadoDE) IS NULL) AND " +
           "(d.fechaEmision >= :fechaInicio OR cast(:fechaInicio as timestamp) IS NULL) AND " +
           "(d.fechaEmision <= :fechaFin OR cast(:fechaFin as timestamp) IS NULL) " +
           "AND d.sucursalId = :sucursalId or (:sucursalId is null) " +
           "ORDER BY d.id DESC")
    Page<DocumentoElectronico> findByFilters(
        @Param("estado") EstadoDE estado,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        @Param("sucursalId") Long sucursalId,
        Pageable pageable
    );

    List<DocumentoElectronico> findByIdIn(List<Long> ids);

    List<DocumentoElectronico> findByLoteDe(com.franco.dev.domain.financiero.LoteDE loteDe);
}
