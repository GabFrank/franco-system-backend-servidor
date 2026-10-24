package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DocumentoRecibidoFoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRecibidoFotoRepository extends JpaRepository<DocumentoRecibidoFoto, Long> {

    List<DocumentoRecibidoFoto> findByDocumentoRecibidoIdOrderByOrdenAsc(Long documentoRecibidoId);
}
