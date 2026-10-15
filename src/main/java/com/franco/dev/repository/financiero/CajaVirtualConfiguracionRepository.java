package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CajaVirtualConfiguracionRepository extends JpaRepository<CajaVirtualConfiguracion, Long> {
    Optional<CajaVirtualConfiguracion> findByCajaVirtualId(Long cajaVirtualId);

    /** true si la cuenta está entre las cuentas bancarias visibles configuradas para la caja. */
    @org.springframework.data.jpa.repository.Query("select count(c) > 0 from CajaVirtualConfiguracion c "
            + "join c.cuentasBancariasVisibles cb where c.cajaVirtual.id = :cajaId and cb.id = :cuentaId")
    boolean esCuentaVisibleDeLaCaja(@org.springframework.data.repository.query.Param("cajaId") Long cajaId,
                                    @org.springframework.data.repository.query.Param("cuentaId") Long cuentaId);
}
