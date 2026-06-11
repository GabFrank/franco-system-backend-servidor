package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.AliasProveedorImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AliasProveedorImportRepository extends JpaRepository<AliasProveedorImport, Long> {

    /** Match exacto por RUC (puede haber multiples — el primero gana). */
    List<AliasProveedorImport> findByRuc(String ruc);

    /** Match exacto por texto OCR (unique constraint). */
    Optional<AliasProveedorImport> findByTextoOcr(String textoOcr);
}
