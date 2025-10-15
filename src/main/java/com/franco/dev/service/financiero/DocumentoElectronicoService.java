package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class DocumentoElectronicoService extends CrudService<DocumentoElectronico, DocumentoElectronicoRepository, Long> {

    private final DocumentoElectronicoRepository repository;

    @Override
    public DocumentoElectronicoRepository getRepository() {
        return repository;
    }

    public Optional<DocumentoElectronico> findByCdc(String cdc) {
        return repository.findByCdc(cdc);
    }

    public Optional<DocumentoElectronico> findByFacturaLegalId(Long facturaLegalId) {
        return repository.findByFacturaLegalId(facturaLegalId);
    }

    public List<DocumentoElectronico> findByEstado(EstadoDE estado) {
        return repository.findByEstado(estado);
    }

    public List<DocumentoElectronico> findByLoteDeIdAndEstado(Long loteId, EstadoDE estado) {
        return repository.findByLoteDeIdAndEstado(loteId, estado);
    }

    public List<DocumentoElectronico> findByLoteDeIdList(Long loteId) {
        return repository.findByLoteDeIdAndEstado(loteId, null);
    }

    public List<DocumentoElectronico> findByIdIn(List<Long> ids) {
        return repository.findByIdIn(ids);
    }

    public Page<DocumentoElectronico> findByFilters(
            EstadoDE estado,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            Pageable pageable) {
        return repository.findByFilters(estado, fechaInicio, fechaFin, pageable);
    }

    @Override
    public DocumentoElectronico save(DocumentoElectronico entity) {
        return super.save(entity);
    }
}
