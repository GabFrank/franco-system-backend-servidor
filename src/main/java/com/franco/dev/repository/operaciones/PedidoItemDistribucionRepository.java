package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoItemDistribucionRepository extends HelperRepository<PedidoItemDistribucion, Long> {
    
    default Class<PedidoItemDistribucion> getEntityClass() {
        return PedidoItemDistribucion.class;
    }

    @Query("SELECT pid FROM PedidoItemDistribucion pid WHERE pid.pedidoItem.id = :pedidoItemId")
    List<PedidoItemDistribucion> findByPedidoItemId(@Param("pedidoItemId") Long pedidoItemId);

    @Query("SELECT pid FROM PedidoItemDistribucion pid WHERE pid.pedidoItem.pedido.id = :pedidoId")
    List<PedidoItemDistribucion> findByPedidoId(@Param("pedidoId") Long pedidoId);

    @Query("SELECT pid FROM PedidoItemDistribucion pid WHERE pid.sucursalEntrega.id = :sucursalId")
    List<PedidoItemDistribucion> findBySucursalEntregaId(@Param("sucursalId") Long sucursalId);

    @Query("SELECT pid FROM PedidoItemDistribucion pid WHERE pid.sucursalInfluencia.id = :sucursalId")
    List<PedidoItemDistribucion> findBySucursalInfluenciaId(@Param("sucursalId") Long sucursalId);

    @Modifying
    @Query("DELETE FROM PedidoItemDistribucion pid WHERE pid.pedidoItem.id = :pedidoItemId")
    void deleteByPedidoItemId(@Param("pedidoItemId") Long pedidoItemId);
} 