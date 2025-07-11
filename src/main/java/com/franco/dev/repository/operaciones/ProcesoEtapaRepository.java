package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcesoEtapaRepository extends HelperRepository<ProcesoEtapa, Long> {
    
    default Class<ProcesoEtapa> getEntityClass() {
        return ProcesoEtapa.class;
    }

    @Query("SELECT pe FROM ProcesoEtapa pe WHERE pe.pedido.id = :pedidoId ORDER BY pe.tipoEtapa")
    List<ProcesoEtapa> findByPedidoIdOrderByTipoEtapa(@Param("pedidoId") Long pedidoId);

    @Query("SELECT pe FROM ProcesoEtapa pe WHERE pe.pedido.id = :pedidoId AND pe.tipoEtapa = :tipoEtapa")
    Optional<ProcesoEtapa> findByPedidoIdAndTipoEtapa(@Param("pedidoId") Long pedidoId, @Param("tipoEtapa") ProcesoEtapaTipo tipoEtapa);

    @Query("SELECT pe FROM ProcesoEtapa pe WHERE pe.estadoEtapa = :estado ORDER BY pe.creadoEn DESC")
    Page<ProcesoEtapa> findByEstadoEtapa(@Param("estado") ProcesoEtapaEstado estado, Pageable pageable);
} 