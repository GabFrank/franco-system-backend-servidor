package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransferenciaItemRepository extends HelperRepository<TransferenciaItem, Long> {
    default Class<TransferenciaItem> getEntityClass() {
        return TransferenciaItem.class;
    }

//    public List<SolicitudCompra> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

    //    @Query("select p from SolicitudCompra p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<SolicitudCompra> findByProveedor(String texto);
    public List<TransferenciaItem> findByTransferenciaId(Long id);

    public Page<TransferenciaItem> findByTransferenciaIdOrderByIdDesc(Long id, Pageable pageable);

    @Query(value = "select t from TransferenciaItem t " +
            "left join t.presentacionPreTransferencia pre1 " +
            "left join t.presentacionPreparacion pre2 " +
            "left join t.presentacionTransporte pre3 " +
            "left join t.presentacionRecepcion pre4 " +
            "join t.transferencia trans " +
            "left join pre1.producto pro1 " +
            "left join pre2.producto pro2 " +
            "left join pre3.producto pro3 " +
            "left join pre4.producto pro4 " +
            "where ( " +
            "((:name) is null or pro1.descripcion like (:name) ) or " +
            "((:name) is null or pro2.descripcion like (:name) ) or " +
            "((:name) is null or pro3.descripcion like (:name) ) or " +
            "((:name) is null or pro4.descripcion like (:name) ) " +
            ") and " +
            "trans.id = :id " +
            "order by t.id desc")
    public Page<TransferenciaItem> findByTransferenciaIdWithFilters(
            @Param("id") Long id,
            @Param("name") String name,
            Pageable pageable);

    public List<TransferenciaItem> findByTransferenciaIdOrderByIdDesc(Long id);//
    public List<TransferenciaItem> findByTransferenciaIdOrderByIdAsc(Long id);//
    // public List<Transferencia> findBySucursalDestinoId(Long id);
//
//    @Query("select e from Transferencia e " +
//            " where cast(e.creadoEn as date) = cast(?1 as date) and ?2 = ?1" +
//            "or cast(e.creadoEn as date) >= cast(?1 as date) AND cast(e.creadoEn as date) <= cast(?2 as date)")
//    public List<Transferencia> findByDate(String start, String end);
}