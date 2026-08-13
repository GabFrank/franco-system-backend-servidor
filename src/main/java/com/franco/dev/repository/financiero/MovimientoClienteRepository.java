package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoCliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoClienteRepository extends JpaRepository<MovimientoCliente, Long> {
    Page<MovimientoCliente> findByClienteIdOrderByCreadoEnDesc(Long clienteId, Pageable pageable);
}
