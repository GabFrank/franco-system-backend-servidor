package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaVirtualAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CajaVirtualAccesoRepository extends JpaRepository<CajaVirtualAcceso, Long> {

    List<CajaVirtualAcceso> findByCajaVirtualIdOrderByIdAsc(Long cajaVirtualId);

    Optional<CajaVirtualAcceso> findByCajaVirtualIdAndUsuarioId(Long cajaVirtualId, Long usuarioId);

    void deleteByCajaVirtualIdAndUsuarioId(Long cajaVirtualId, Long usuarioId);

    /**
     * Ids de las cajas que el usuario puede ver: las que le fueron otorgadas mas las que
     * son suyas. Es la consulta caliente — filtra todos los listados de cajas.
     */
    @Query("select c.id from CajaVirtual c "
            + "where c.usuario.id = :usuarioId "
            + "   or exists (select 1 from CajaVirtualAcceso a "
            + "              where a.cajaVirtual.id = c.id and a.usuario.id = :usuarioId and a.puedeLeer = true)")
    List<Long> cajasVisiblesIds(@Param("usuarioId") Long usuarioId);

    /**
     * Sucursales de las cajas que el usuario puede mover (propias o con escritura otorgada).
     * Las cajas sin sucursal quedan afuera.
     */
    @Query("select distinct c.sucursal.id from CajaVirtual c "
            + "where c.sucursal is not null and (c.usuario.id = :usuarioId "
            + "   or exists (select 1 from CajaVirtualAcceso a "
            + "              where a.cajaVirtual.id = c.id and a.usuario.id = :usuarioId and a.puedeEscribir = true))")
    List<Long> sucursalesOperablesIds(@Param("usuarioId") Long usuarioId);
}
