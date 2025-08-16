package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface DocumentoElectronicoRepository extends HelperRepository<DocumentoElectronico, Long> {
    default Class<DocumentoElectronico> getEntityClass() {
        return DocumentoElectronico.class;
    }

    List<DocumentoElectronico> findTop50ByEstadoSifenOrderByIdAsc(String estado);

    List<DocumentoElectronico> findByLoteId(Long loteId);

    Page<DocumentoElectronico> findByEstadoSifen(String estadoSifen, Pageable pageable);

    Page<DocumentoElectronico> findByCreadoEnBetween(LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<DocumentoElectronico> findByEstadoSifenAndCreadoEnBetween(String estadoSifen, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    // Filtros adicionales
    Page<DocumentoElectronico> findByCdcContainingIgnoreCase(String cdc, Pageable pageable);

    Page<DocumentoElectronico> findByFacturaLegal_SucursalId(Long sucursalId, Pageable pageable);

    Page<DocumentoElectronico> findByFacturaLegal_SucursalIdAndEstadoSifen(Long sucursalId, String estadoSifen, Pageable pageable);

    Page<DocumentoElectronico> findByFacturaLegal_SucursalIdAndCreadoEnBetween(Long sucursalId, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<DocumentoElectronico> findByFacturaLegal_SucursalIdAndEstadoSifenAndCreadoEnBetween(Long sucursalId, String estadoSifen, LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    Page<DocumentoElectronico> findByEstadoSifenOrderByIdDesc(String estadoSifen, Pageable pageable);

    DocumentoElectronico findFirstByFacturaLegal_IdAndFacturaLegal_SucursalId(Long facturaLegalId, Long sucursalId);
}


