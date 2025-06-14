package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.dto.DistribucionSucursalResult;
import com.franco.dev.domain.operaciones.enums.PedidoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoItemRepository extends HelperRepository<PedidoItem, Long> {
    default Class<PedidoItem> getEntityClass() {
        return PedidoItem.class;
    }

    public Page<PedidoItem> findByPedidoIdOrderByIdDesc(Long id, Pageable page);
    public List<PedidoItem> findByPedidoId(Long id);

    public List<PedidoItem> findByProductoId(Long id);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    @Query(value = "select * from operaciones.pedido_item pi2 " +
            "where pi2.pedido_id = ?1 and pi2.nota_recepcion_id isnull", nativeQuery = true)
    public List<PedidoItem> findByPedidoIdSobrantes(Long id);

    public Page<PedidoItem> findByNotaRecepcionId(Long id,  Pageable page);
    public Page<PedidoItem> findByNotaRecepcionIdAndVerificadoRecepcionProducto(Long id, Boolean verificado,  Pageable page);
    public Integer countByNotaRecepcionIdAndVerificadoRecepcionProducto(Long id, Boolean verificado);

    public Page<PedidoItem> findByNotaRecepcionIdAndProductoDescripcionLikeOrderByProductoDescripcionDesc(Long id,  String texto, Pageable page);
    public Page<PedidoItem> findByNotaRecepcionIdAndProductoDescripcionLikeAndVerificadoRecepcionProductoOrderByProductoDescripcionDesc(Long id,  String texto, Boolean verificado, Pageable page);


    public List<PedidoItem> findByNotaRecepcionId(Long id);

    public Page<PedidoItem> findByPedidoIdAndNotaRecepcionIdIsNull(Long id, Pageable page);
    public Page<PedidoItem> findByPedidoIdAndNotaRecepcionIdIsNullAndProductoDescripcionLikeOrderByProductoDescripcionDesc(Long id, String texto, Pageable page);


    public Long countByPedidoIdAndNotaRecepcionIdIsNull(Long id);

    public Long countByPedidoIdAndCancelado(Long id, Boolean cancelado);

    public Integer countByNotaRecepcionId(Long id);

    public Integer countByPedidoId(Long id);

    @Query(value = "SELECT COUNT(*) FROM operaciones.pedido_item pi " +
            "WHERE pi.nota_recepcion_id = ?1 " +
            "AND (pi.cancelado IS NULL OR pi.cancelado = false) " +
            "AND (pi.motivo_rechazo_recepcion_nota IS NULL OR pi.motivo_rechazo_recepcion_nota = '')", 
            nativeQuery = true)
    public Integer countByNotaRecepcionIdExcludingRejected(Long id);

    public List<PedidoItem> findByIdIn(List<Long> idList);

    public Page<PedidoItem> findByPedidoIdAndProductoDescripcionLikeOrderByProductoDescripcionDesc(Long id, String texto, Pageable page);

    public Integer countByPedidoIdAndVerificadoRecepcionProductoFalse(Long id);
    public Integer countByPedidoIdAndVerificadoRecepcionNotaFalse(Long id);

    public Page<PedidoItem> findByProductoIdAndPedidoEstado(Long productoId, PedidoEstado estado, Pageable pageable);

    @Query("select " +
            "   case when (pi.cantidadCreacion * pc.cantidad) = " +
            "        (select sum(pis.cantidadPorUnidad) from PedidoItemSucursal pis where pis.pedidoItem = pi) " +
            "        then true else false end " +
            "from PedidoItem pi " +
            "left join pi.presentacionCreacion pc " +
            "left join pi.presentacionRecepcionNota prc " +
            "where pi.id = :id")
    Boolean findDistribucionSucursalCreacionByPedidoItemId(@Param("id") Long id);

    @Query("select " +
            "   case when (pi.cantidadCreacion * prc.cantidad) = " +
            "        (select sum(pis2.cantidadPorUnidad) from PedidoItemSucursal pis2 where pis2.pedidoItem = pi) " +
            "        then true else false end " +
            "from PedidoItem pi " +
            "left join pi.presentacionCreacion pc " +
            "left join pi.presentacionRecepcionNota prc " +
            "where pi.id = :id")
    Boolean findDistribucionSucursalRecepcionByPedidoItemId(@Param("id") Long id);

    @Query("select case when count(case when (pi.cantidadCreacion * prc.cantidad) = " +
            "       (select sum(pis2.cantidadPorUnidad) " +
            "        from PedidoItemSucursal pis2 " +
            "        where pis2.pedidoItem = pi) then 1 end) = count(pi) " +
            "then true else false end " +
            "from PedidoItem pi " +
            "left join pi.presentacionCreacion pc " +
            "left join pi.presentacionRecepcionNota prc " +
            "left join pi.pedido p " +
            "where p.id = :id")
    Boolean findDistribucionSucursalRecepcionByPedidoId(@Param("id") Long id);

    public Long countByPedidoIdAndNotaRecepcionIdIsNotNull(Long id);

    /**
     * Get total distributed quantity for a specific PedidoItem from PedidoItemSucursal
     */
    @Query("select coalesce(sum(pis.cantidadPorUnidad), 0.0) " +
           "from PedidoItemSucursal pis " +
           "where pis.pedidoItem.id = :pedidoItemId")
    Double getTotalDistributedQuantityByPedidoItemId(@Param("pedidoItemId") Long pedidoItemId);

}

