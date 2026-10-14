package com.franco.dev.repository.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaVirtualSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CajaVirtualSucursalRepository extends JpaRepository<CajaVirtualSucursal, Long> {

    List<CajaVirtualSucursal> findByCajaVirtualIdOrderByIdAsc(Long cajaVirtualId);

    /** Las sucursales adicionales de la caja, sin la propia. */
    @Query("select cs.sucursal from CajaVirtualSucursal cs where cs.cajaVirtual.id = :cajaId order by cs.sucursal.nombre")
    List<Sucursal> sucursalesAdicionales(@Param("cajaId") Long cajaId);
}
