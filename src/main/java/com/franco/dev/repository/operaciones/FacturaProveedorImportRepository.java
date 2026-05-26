package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.FacturaProveedorImport;
import com.franco.dev.domain.operaciones.enums.EstadoImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacturaProveedorImportRepository extends JpaRepository<FacturaProveedorImport, Long> {
    Page<FacturaProveedorImport> findByEstadoOrderByCreadoEnDesc(EstadoImport estado, Pageable pageable);
    Page<FacturaProveedorImport> findAllByOrderByCreadoEnDesc(Pageable pageable);
}
