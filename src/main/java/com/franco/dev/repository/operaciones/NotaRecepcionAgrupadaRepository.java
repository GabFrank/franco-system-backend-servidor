package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotaRecepcionAgrupadaRepository extends HelperRepository<NotaRecepcionAgrupada, Long> {
    default Class<NotaRecepcionAgrupada> getEntityClass() {
        return NotaRecepcionAgrupada.class;
    }

    public Page<NotaRecepcionAgrupada> findByUsuarioIdOrderByIdDesc(Long id, Pageable page);

    public Page<NotaRecepcionAgrupada> findByProveedorId(Long id, Pageable page);

    @Query(value = "SELECT  " +
            "            pi.producto_id AS productoId,  " +
            "            SUM(pis.cantidad_por_unidad) AS totalCantidadARecibirPorUnidad, " +
            "            SUM(pis.cant_por_unidad_recibida) AS totalCantidadRecibidaPorUnidad " +
            "        FROM operaciones.pedido_item pi " +
            "        LEFT JOIN operaciones.pedido_item_sucursal pis ON pis.pedido_item_id = pi.id " +
            "        LEFT JOIN operaciones.pedido p ON p.id = pi.pedido_id  " +
            "        LEFT JOIN operaciones.nota_recepcion nr ON nr.pedido_id = p.id  " +
            "        LEFT JOIN operaciones.nota_recepcion_agrupada nra ON nra.id = nr.nota_recepcion_agrupada_id  " +
            "        WHERE nra.id = :notaRecepcionAgrupadaId " +
            "        GROUP BY pi.producto_id", nativeQuery = true,
            countQuery = "SELECT COUNT(DISTINCT pi.producto_id) " +
                    "        FROM operaciones.pedido_item pi  " +
                    "        LEFT JOIN operaciones.pedido p ON p.id = pi.pedido_id  " +
                    "        LEFT JOIN operaciones.nota_recepcion nr ON nr.pedido_id = p.id  " +
                    "        LEFT JOIN operaciones.nota_recepcion_agrupada nra ON nra.id = nr.nota_recepcion_agrupada_id  " +
                    "        WHERE nra.id = :notaRecepcionAgrupadaId")
    public Page<PedidoRecepcionProductoDto> findRecepcionProductoByRecepcionByNotaAgrupada(@Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId, Pageable page);

    @Query(value = "SELECT  " +
            "            pi.producto_id AS productoId,  " +
            "            SUM(pis.cantidad_por_unidad) AS totalCantidadARecibirPorUnidad, " +
            "            SUM(pis.cant_por_unidad_recibida) AS totalCantidadRecibidaPorUnidad " +
            "        FROM operaciones.pedido_item pi " +
            "        LEFT JOIN operaciones.pedido_item_sucursal pis ON pis.pedido_item_id = pi.id " +
            "        LEFT JOIN operaciones.pedido p ON p.id = pi.pedido_id  " +
            "        LEFT JOIN operaciones.nota_recepcion nr ON nr.pedido_id = p.id  " +
            "        LEFT JOIN operaciones.nota_recepcion_agrupada nra ON nra.id = nr.nota_recepcion_agrupada_id  " +
            "        WHERE nra.id = :notaRecepcionAgrupadaId and pi.producto_id = :productoId" +
            "        GROUP BY pi.producto_id", nativeQuery = true)
    public PedidoRecepcionProductoDto findRecepcionProductoByRecepcionByNotaAgrupadaAndProducto(@Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId, @Param("productoId") Long productoId);
}