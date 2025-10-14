package com.franco.dev.repository.financiero;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.repository.HelperRepository;

public interface DocumentoElectronicoRepository extends HelperRepository <DocumentoElectronico, Long>{

  default Class<DocumentoElectronico> getEntityClass() {
    return DocumentoElectronico.class;
  }

  DocumentoElectronico findByFacturaLegalId(Long id);

  DocumentoElectronico findByLoteDeId(Long id);

  @Query(value = "select * from financiero.documento_electronico de where de.lote_de_id = :id order by de.id asc", nativeQuery = true)
  List<DocumentoElectronico> findByLoteDeIdOrderByIdAsc(@Param("id") Long id);

  DocumentoElectronico findByCdc(String cdc);

  DocumentoElectronico findBySucursalId(Long id);

  @Query(value = "select de from DocumentoElectronico de where " +
  "(de.creadoEn between :inicio and :fin) " + 
  "and (de.sucursalId in :sucId) order by de.creadoEn asc")
  public Page<DocumentoElectronico> findByCreadoEnBetweenAndSucursalId(Pageable pageable, LocalDateTime inicio, LocalDateTime fin, Long sucId);
  
  DocumentoElectronico findByEstado(EstadoDE estado);
}
