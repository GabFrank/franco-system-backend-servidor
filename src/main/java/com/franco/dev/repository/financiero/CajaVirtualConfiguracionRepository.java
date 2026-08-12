package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CajaVirtualConfiguracionRepository extends JpaRepository<CajaVirtualConfiguracion, Long> {
    Optional<CajaVirtualConfiguracion> findByCajaVirtualId(Long cajaVirtualId);
}
