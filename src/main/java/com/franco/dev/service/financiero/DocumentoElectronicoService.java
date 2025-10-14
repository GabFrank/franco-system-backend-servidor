package com.franco.dev.service.financiero;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class DocumentoElectronicoService extends CrudService<DocumentoElectronico, DocumentoElectronicoRepository, Long>{

  private final DocumentoElectronicoRepository repository;
  
  @Override
  public DocumentoElectronicoRepository getRepository() {
    return repository;
  }
  
  public DocumentoElectronico findByFacturaLegalId(Long id) {
    return repository.findByFacturaLegalId(id);
  }

  public DocumentoElectronico findByLoteDeId(Long id) {
    return repository.findByLoteDeId(id);
  }

  public java.util.List<DocumentoElectronico> findByLoteDeIdList(Long id) {
    return repository.findByLoteDeIdOrderByIdAsc(id);
  }

  public DocumentoElectronico findByCdc(String cdc) {
    return repository.findByCdc(cdc);
  }

  public DocumentoElectronico findBySucursalId(Long id) {
    return repository.findBySucursalId(id);
  }

  public Page<DocumentoElectronico> findByAll(Integer page, Integer size, String fechaInicio, String fechaFin, Long sucId) {
    LocalDateTime inicio = stringToDate(fechaInicio);
    LocalDateTime fin = stringToDate(fechaFin);
    Pageable pageable = PageRequest.of(page, size);
    return repository.findByCreadoEnBetweenAndSucursalId(pageable, inicio, fin, sucId);
  }

  public DocumentoElectronico findByEstado(EstadoDE estado) {
    return repository.findByEstado(estado);
  }

  @Override
  public DocumentoElectronico save(DocumentoElectronico entity) {
    if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
    DocumentoElectronico e = super.save(entity);
    return e;
  }
  
}
