package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EntradaVariaCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EntradaVariaCategoriaRepository extends JpaRepository<EntradaVariaCategoria, Long> {
    List<EntradaVariaCategoria> findByActivoTrue();
    List<EntradaVariaCategoria> findByPadreId(Long padreId);
}
