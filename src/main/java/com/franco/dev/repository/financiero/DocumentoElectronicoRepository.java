package com.franco.dev.repository.financiero;

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

public interface DocumentoElectronicoRepository extends HelperRepository<DocumentoElectronico, Long> {

    default Class<DocumentoElectronico> getEntityClass() {
        return DocumentoElectronico.class;
    }

    List<DocumentoElectronico> findAllByOrderByIdAsc(Pageable pageable);

    Optional<DocumentoElectronico> findByCdc(String cdc);
    
    Optional<DocumentoElectronico> findByFacturaLegalId(Long facturaLegalId);

    List<DocumentoElectronico> findByEstado(EstadoDE estado);

    List<DocumentoElectronico> findByLoteDeIdAndEstado(Long loteId, EstadoDE estado);

    @Query("SELECT d FROM DocumentoElectronico d WHERE " +
           "(d.estado = :estado OR cast(:estado as com.franco.dev.domain.financiero.enums.EstadoDE) IS NULL) AND " +
           "(d.fechaEmision >= :fechaInicio OR cast(:fechaInicio as timestamp) IS NULL) AND " +
           "(d.fechaEmision <= :fechaFin OR cast(:fechaFin as timestamp) IS NULL) " +
           "ORDER BY d.id DESC")
    Page<DocumentoElectronico> findByFilters(
        @Param("estado") EstadoDE estado,
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin,
        Pageable pageable
    );

    List<DocumentoElectronico> findByIdIn(List<Long> ids);

    List<DocumentoElectronico> findByLoteDe(com.franco.dev.domain.financiero.LoteDE loteDe);
}
