package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoRechazo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
            "((:id) is null or trans.id = :id) " +
            "order by t.id asc")
    public Page<TransferenciaItem> findByTransferenciaIdWithFilters(
            @Param("id") Long id,
            @Param("name") String name,
            Pageable pageable);

    String RECHAZADOS_WHERE = "where (ti.motivoRechazoPreTransferencia = :motivo or ti.motivoRechazoPreparacion = :motivo " +
            "or ti.motivoRechazoTransporte = :motivo or ti.motivoRechazoRecepcion = :motivo) " +
            "and (ti.activo is null or ti.activo = true) " +
            "and (:sucursalOrigenId is null or t.sucursalOrigen.id = :sucursalOrigenId) " +
            "and (:sucursalDestinoId is null or t.sucursalDestino.id = :sucursalDestinoId) " +
            "and (:productoId is null or pp.producto.id = :productoId) " +
            "and t.creadoEn >= :desde and t.creadoEn <= :hasta ";

    /**
     * Items rechazados con {@code motivo} en cualquier etapa: el motivo se copia hacia adelante y una
     * etapa posterior lo puede cambiar, asi que no alcanza con el primero no nulo. Sin indice que la
     * cubra, recorre transferencia_item entera (~120 ms con 808k): el resolver obliga a un rango y el
     * timeout corta el peor caso.
     */
    @QueryHints(@QueryHint(name = "javax.persistence.query.timeout", value = "10000"))
    @Query(value = "select ti from TransferenciaItem ti " +
            "join fetch ti.transferencia t " +
            "join fetch ti.presentacionPreTransferencia pp " +
            "join fetch pp.producto " +
            RECHAZADOS_WHERE +
            "order by t.creadoEn desc, ti.id desc",
            countQuery = "select count(ti) from TransferenciaItem ti " +
                    "join ti.transferencia t " +
                    "join ti.presentacionPreTransferencia pp " +
                    RECHAZADOS_WHERE)
    Page<TransferenciaItem> findRechazados(
            @Param("motivo") TransferenciaItemMotivoRechazo motivo,
            @Param("sucursalOrigenId") Long sucursalOrigenId,
            @Param("sucursalDestinoId") Long sucursalDestinoId,
            @Param("productoId") Long productoId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);

    public List<TransferenciaItem> findByTransferenciaIdOrderByIdDesc(Long id);//
    public List<TransferenciaItem> findByTransferenciaIdOrderByIdAsc(Long id);//

    List<TransferenciaItem> findByIdInAndTransferenciaId(List<Long> ids, Long transferenciaId);

    /** Id de la transferencia del item sin cargar la entidad: para bloquear la transferencia antes que el item (#468). */
    @Query("select t.transferencia.id from TransferenciaItem t where t.id = :id")
    Long transferenciaIdDe(@Param("id") Long id);

    /** Lee el item con {@code FOR UPDATE}: dos saves del mismo item se serializan (#468). Primera carga del item. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferenciaItem t where t.id = :id")
    Optional<TransferenciaItem> lockById(@Param("id") Long id);
    // public List<Transferencia> findBySucursalDestinoId(Long id);
//
//    @Query("select e from Transferencia e " +
//            " where cast(e.creadoEn as date) = cast(?1 as date) and ?2 = ?1" +
//            "or cast(e.creadoEn as date) >= cast(?1 as date) AND cast(e.creadoEn as date) <= cast(?2 as date)")
//    public List<Transferencia> findByDate(String start, String end);
}