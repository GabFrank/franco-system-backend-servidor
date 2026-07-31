package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoBancarioRepository extends JpaRepository<MovimientoBancario, Long> {
    Page<MovimientoBancario> findByCuentaBancariaIdOrderByCreadoEnDesc(Long cuentaBancariaId, Pageable pageable);
}
