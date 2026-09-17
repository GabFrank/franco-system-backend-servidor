package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.LoteDE;
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
public class DocumentoElectronicoService extends CrudService<DocumentoElectronico, DocumentoElectronicoRepository, EmbebedPrimaryKey> {

    private final DocumentoElectronicoRepository repository;

    @Override
    public DocumentoElectronicoRepository getRepository() {
        return repository;
    }

    public Optional<DocumentoElectronico> findByCdc(String cdc) {
        return repository.findByCdc(cdc);
    }

    public Optional<DocumentoElectronico> findByFacturaLegalId(Long facturaLegalId, Long sucursalId) {
        return repository.findByFacturaLegalId(facturaLegalId, sucursalId);
    }

    public List<DocumentoElectronico> findByEstado(EstadoDE estado) {
        return repository.findByEstado(estado);
    }

    public List<DocumentoElectronico> findByLoteDeIdAndSucursalIdAndEstado(Long loteId, Long sucursalId, EstadoDE estado) {
        return repository.findByLoteDeIdAndSucursalIdAndEstado(loteId, sucursalId, estado);
    }

    public List<DocumentoElectronico> findByLoteDeIdList(Long loteId) {
        return repository.findByLoteDeIdAndSucursalIdAndEstado(loteId, null, null);
    }

    public List<DocumentoElectronico> findByIdIn(List<Long> ids) {
        return repository.findByIdIn(ids);
    }

    public Page<DocumentoElectronico> findByFilters(
            EstadoDE estado,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            Long sucursalId,
            Pageable pageable) {
        return repository.findByFilters(estado, fechaInicio, fechaFin, sucursalId, pageable);
    }

    public List<DocumentoElectronico> findByLoteDe(LoteDE loteDe) {
        return repository.findByLoteDe(loteDe);
    }

    public DocumentoElectronico createFromFacturaLegal(FacturaLegal facturaLegal) {
        DocumentoElectronico documentoElectronico = new DocumentoElectronico();
        documentoElectronico.setFacturaLegal(facturaLegal);
        documentoElectronico.setSucursalId(facturaLegal.getSucursalId());
        documentoElectronico.setNumeroDocumento(facturaLegal.getNumeroFactura().toString());
        documentoElectronico.setTipoDocumento("FACTURA");
        documentoElectronico.setFechaEmision(facturaLegal.getFecha());
        documentoElectronico.setActivo(true);
        documentoElectronico.setUsuario(facturaLegal.getUsuario());
        documentoElectronico.setEstado(EstadoDE.PENDIENTE);
        
        return documentoElectronico;
    }

    /**
     * DE de una nota de remision: sin factura, con la FK a la nota y tipo NOTA_REMISION. El estado
     * y el resto de los campos los completa SifenService (CDC, XML, QR).
     */
    public DocumentoElectronico createFromNotaRemision(com.franco.dev.domain.financiero.NotaRemision nota) {
        DocumentoElectronico documentoElectronico = new DocumentoElectronico();
        documentoElectronico.setSucursalId(nota.getSucursalId());
        documentoElectronico.setNotaRemisionId(nota.getId());
        documentoElectronico.setNumeroDocumento(String.valueOf(nota.getNumeroNotaRemision()));
        documentoElectronico.setTipoDocumento(
                com.franco.dev.service.sifen.util.TipoDocumentoElectronico.NOTA_REMISION);
        documentoElectronico.setFechaEmision(nota.getFecha());
        documentoElectronico.setActivo(true);
        documentoElectronico.setEstado(EstadoDE.PENDIENTE);
        return documentoElectronico;
    }

    public Optional<DocumentoElectronico> findByNotaRemisionId(Long notaRemisionId, Long sucursalId) {
        return repository.findByNotaRemisionId(notaRemisionId, sucursalId);
    }

    @Override
    public DocumentoElectronico save(DocumentoElectronico entity) {
        return super.save(entity);
    }
}
