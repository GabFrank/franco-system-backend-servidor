package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoProveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoProveedorRepository extends JpaRepository<MovimientoProveedor, Long> {
    Page<MovimientoProveedor> findByProveedorIdOrderByCreadoEnDesc(Long proveedorId, Pageable pageable);
}
