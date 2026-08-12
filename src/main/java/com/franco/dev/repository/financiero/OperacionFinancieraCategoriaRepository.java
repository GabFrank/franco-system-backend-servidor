package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.OperacionFinancieraCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OperacionFinancieraCategoriaRepository extends JpaRepository<OperacionFinancieraCategoria, Long> {
    List<OperacionFinancieraCategoria> findByActivoTrue();
}
