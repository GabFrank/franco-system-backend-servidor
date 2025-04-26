package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
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
                    "LEFT JOIN PedidoItemSucursal pis ON pis.pedidoItem.id = pi2.id " +
                    "LEFT JOIN NotaRecepcion nr ON nr.pedido.id = p.id " +
                    "WHERE " +
                    "(cast(:estado as com.franco.dev.domain.operaciones.enums.PedidoEstado) IS NULL OR p.estado = :estado) " +
                    "AND (:sucursalId IS NULL OR pis.sucursal.id = :sucursalId) " +
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
            PedidoEstado estado,
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


    Page<Pedido> findById(Long idPedido, Pageable page);
}