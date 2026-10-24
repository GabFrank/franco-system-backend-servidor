package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DocumentoRecibidoFoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoRecibidoFotoRepository extends JpaRepository<DocumentoRecibidoFoto, Long> {

    List<DocumentoRecibidoFoto> findByDocumentoRecibidoIdOrderByOrdenAsc(Long documentoRecibidoId);

    /** Una sola hoja: así no se traen los bytes de todas. */
    Optional<DocumentoRecibidoFoto> findFirstByDocumentoRecibidoIdAndOrden(Long documentoRecibidoId, Integer orden);
}
