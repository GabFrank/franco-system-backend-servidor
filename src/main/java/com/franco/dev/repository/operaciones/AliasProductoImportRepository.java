package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.AliasProductoImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AliasProductoImportRepository extends JpaRepository<AliasProductoImport, Long> {

    /** Match exacto codigo OCR (puede haber varios proveedores con mismo codigo). */
    List<AliasProductoImport> findByCodigoOcr(String codigoOcr);

    /** Match exacto texto OCR + proveedor (unique). */
    Optional<AliasProductoImport> findByTextoOcrAndProveedorId(String textoOcr, Long proveedorId);
}
