package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoResumen;

import com.franco.dev.repository.HelperRepository;

import org.bouncycastle.util.Times;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public interface PedidoRepository extends HelperRepository<Pedido, Long> {
    default Class<Pedido> getEntityClass() {
        return Pedido.class;
    }

    @Query(
            value = "SELECT DISTINCT p " +
                    "FROM Pedido p " +
                    "LEFT JOIN PedidoItem pi2 ON pi2.pedido.id = p.id " +
                    "LEFT JOIN PedidoItemDistribucion pis ON pis.pedidoItem.id = pi2.id " +
                    "LEFT JOIN NotaRecepcion nr ON nr.pedido.id = p.id " +
                    "WHERE " +
                    "(:sucursalId IS NULL OR pis.sucursalInfluencia.id = :sucursalId) " +
                    "AND (:idPedido IS NULL OR p.id = :idPedido) " +
                    "AND (:numeroNotaRecepcion IS NULL OR nr.numero = :numeroNotaRecepcion) " +
                    "AND (cast(:inicio as timestamp) IS NULL OR p.creadoEn BETWEEN :inicio AND :fin) " +
                    "AND (:proveedorId IS NULL OR p.proveedor.id = :proveedorId) " +
                    "AND (:vendedorId IS NULL OR p.vendedor.id = :vendedorId) " +
                    "AND (:formaPago IS NULL OR p.formaPago.id = :formaPago) " +
                    "AND (:productoId IS NULL OR pi2.producto.id = :productoId) " +
                    "ORDER BY p.id DESC"
    )
    Page<Pedido> filterPedidos(
            Long idPedido,
            Integer numeroNotaRecepcion,
            Long sucursalId,
            LocalDateTime inicio,
            LocalDateTime fin,
            Long proveedorId,
            Long vendedorId,
            Long formaPago,
            Long productoId,
            Pageable page
    );

    @Query(
            value = "SELECT DISTINCT p " +
                    "FROM Pedido p " +
                    "LEFT JOIN NotaRecepcion nr ON nr.pedido.id = p.id " +
                    "WHERE " +
                    "(:numeroNotaRecepcion IS NULL OR nr.numero = :numeroNotaRecepcion) " +
                    "ORDER BY p.id DESC"
    )
    Page<Pedido> filterPedidosByNumeroNota(Integer numeroNotaRecepcion, Pageable page);

    /**
     * Obtiene el resumen del pedido con cantidad de ítems y valor total
     * @param pedidoId ID del pedido
     * @return Array con [cantidadItems, valorTotal]
     */


    Page<Pedido> findById(Long idPedido, Pageable page);

    @Query(
            value = "SELECT COUNT(pi.id) " +
                    "FROM operaciones.pedido_item pi " +
                    "WHERE pi.pedido_id = :pedidoId",
            nativeQuery = true
    )
    Long getCantidadItemsByPedidoId(@Param("pedidoId") Long pedidoId);

    @Query(
            value = "SELECT COALESCE(SUM(pi.cantidad_solicitada * pi.precio_unitario_solicitado), 0.0) " +
                    "FROM operaciones.pedido_item pi " +
                    "WHERE pi.pedido_id = :pedidoId",
            nativeQuery = true
    )
    Double getValorTotalByPedidoId(@Param("pedidoId") Long pedidoId);

    /**
     * Obtiene el valor total de las facturas actuales (notas de recepción)
     * @param pedidoId ID del pedido
     * @return Valor total de las notas de recepción
     */
    @Query(
            value = "SELECT COALESCE(SUM(nri.cantidad_en_nota * nri.precio_unitario_en_nota), 0.0) " +
                    "FROM operaciones.nota_recepcion_item nri " +
                    "JOIN operaciones.nota_recepcion nr ON nr.id = nri.nota_recepcion_id " +
                    "WHERE nr.pedido_id = :pedidoId " +
                    "AND nr.es_nota_rechazo = false",
            nativeQuery = true
    )
    Double getValorTotalNotasRecepcionByPedidoId(@Param("pedidoId") Long pedidoId);

    @Query(
            value = "SELECT COUNT(pi.id) " +
                    "FROM operaciones.pedido_item pi " +
                    "WHERE pi.pedido_id = :pedidoId " +
                    "AND EXISTS (" +
                    "  SELECT 1 FROM operaciones.pedido_item_distribucion pid " +
                    "  WHERE pid.pedido_item_id = pi.id " +
                    "  HAVING COALESCE(SUM(pid.cantidad_asignada), 0) = pi.cantidad_solicitada" +
                    ")",
            nativeQuery = true
    )
    Long getCantidadItemsConDistribucionCompleta(@Param("pedidoId") Long pedidoId);


}