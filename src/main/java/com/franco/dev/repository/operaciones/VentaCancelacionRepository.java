package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.VentaCancelacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaCancelacionRepository extends JpaRepository<VentaCancelacion, Long> {

    VentaCancelacion findFirstByVentaIdAndSucursalIdOrderByIdDesc(Long ventaId, Long sucursalId);
}
