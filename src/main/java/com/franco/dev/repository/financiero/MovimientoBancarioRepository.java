package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MovimientoBancario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoBancarioRepository extends JpaRepository<MovimientoBancario, Long> {
    Page<MovimientoBancario> findByCuentaBancariaIdOrderByCreadoEnDesc(Long cuentaBancariaId, Pageable pageable);

    /** Patas bancarias no anuladas de una operación dueña (para revertir todas al anularla). */
    List<MovimientoBancario> findByOrigenTipoAndOrigenIdAndAnuladoFalse(String origenTipo, Long origenId);
}
