package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.domain.operaciones.enums.PedidoRecepcionProductoEstado;
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

//    @Query(
//            value = "SELECT new com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto(" +
//                    "    prod, " +
//                    "    SUM(pis.cantidadPorUnidad), " +
//                    "    SUM(pis.cantidadPorUnidadRecibida)" +
//                    ") " +
//                    "FROM PedidoItem pi " +
//                    "JOIN pi.producto prod " +
//                    "JOIN PedidoItemSucursal pis ON pis.pedidoItem = pi " +
//                    "JOIN pi.pedido p " +
//                    "JOIN NotaRecepcion nr ON nr.pedido = p " +
//                    "JOIN nr.notaRecepcionAgrupada nra " +
//                    "WHERE nra.id = :notaRecepcionAgrupadaId " +
//                    "  AND pis.sucursalEntrega = nra.sucursal " +
//                    "GROUP BY prod " +
//                    "HAVING (:estado IS NULL OR " +
//                    "        (CASE " +
//                    "           WHEN SUM(pis.cantidadPorUnidadRecibida) IS NULL THEN 'PENDIENTE' " +
//                    "           WHEN SUM(pis.cantidadPorUnidadRecibida) >= SUM(pis.cantidadPorUnidad) THEN 'RECIBIDO' " +
//                    "           ELSE 'RECIBIDO_PARCIALMENTE' " +
//                    "         END) = :estado)"
//    )
//    public Page<PedidoRecepcionProductoDto> findRecepcionProductoByRecepcionByNotaAgrupada(
//            @Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId,
//            @Param("estado") String estado,
//            Pageable pageable);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto(" +
            "    prod, " +
            "    SUM(pis.cantidadPorUnidad), " +
            "    SUM(pis.cantidadPorUnidadRecibida) " +
            ") " +
            "FROM NotaRecepcionAgrupada nra " +
            "JOIN NotaRecepcion nr WITH nr.notaRecepcionAgrupada = nra " +
            "JOIN PedidoItem pi WITH pi.notaRecepcion = nr " +
            "JOIN PedidoItemSucursal pis WITH pis.pedidoItem = pi AND pis.sucursalEntrega = nra.sucursal " +
            "JOIN pi.presentacionRecepcionNota pre " +
            "JOIN pre.producto prod " +
            "WHERE nra.id = :notaRecepcionAgrupadaId " +
            "GROUP BY prod.id, prod.descripcion " +
            "HAVING (:estado IS NULL OR  " +
            "       (CASE  " +
            "          WHEN SUM(pis.cantidadPorUnidadRecibida) IS NULL THEN 'PENDIENTE' " +
            "          WHEN SUM(pis.cantidadPorUnidadRecibida) >= SUM(pis.cantidadPorUnidad) THEN 'RECIBIDO' " +
            "          ELSE 'RECIBIDO_PARCIALMENTE' " +
            "        END) = :estado) ")
    Page<PedidoRecepcionProductoDto> findRecepcionProductoByRecepcionByNotaAgrupada(
            @Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId,
            @Param("estado") String estado,
            Pageable pageable);




    @Query("SELECT new com.franco.dev.domain.operaciones.dto.PedidoRecepcionProductoDto(" +
            "    prod, " +
            "    SUM(pis.cantidadPorUnidad), " +
            "    SUM(pis.cantidadPorUnidadRecibida)" +
            ") " +
            "FROM PedidoItem pi " +
            "LEFT JOIN pi.producto prod " +
            "LEFT JOIN PedidoItemSucursal pis ON pis.pedidoItem = pi " +
            "LEFT JOIN pi.pedido p " +
            "LEFT JOIN NotaRecepcion nr ON nr.pedido = p " +
            "LEFT JOIN nr.notaRecepcionAgrupada nra " +
            "WHERE nra.id = :notaRecepcionAgrupadaId " +
            "  AND prod.id = :productoId AND pis.sucursal = nra.sucursal " +
            "GROUP BY prod " +
            "HAVING (:estado IS NULL OR " +
            "        (CASE " +
            "           WHEN SUM(pis.cantidadPorUnidadRecibida) IS NULL THEN 'PENDIENTE' " +
            "           WHEN SUM(pis.cantidadPorUnidadRecibida) >= SUM(pis.cantidadPorUnidad) THEN 'RECIBIDO' " +
            "           ELSE 'RECIBIDO_PARCIALMENTE' " +
            "         END) = :estado)")
    public PedidoRecepcionProductoDto findRecepcionProductoByRecepcionByNotaAgrupadaAndProducto(
            @Param("notaRecepcionAgrupadaId") Long notaRecepcionAgrupadaId,
            @Param("productoId") Long productoId,
            @Param("estado") String estado);


}
