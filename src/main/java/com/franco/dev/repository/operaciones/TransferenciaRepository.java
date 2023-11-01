package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransferenciaRepository extends HelperRepository<Transferencia, Long> {
    default Class<Transferencia> getEntityClass() {
        return Transferencia.class;
    }

//    public List<SolicitudCompra> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from SolicitudCompra p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<SolicitudCompra> findByProveedor(String texto);

    public List<Transferencia> findBySucursalOrigenId(Long id);
    public List<Transferencia> findBySucursalDestinoId(Long id);

    @Query("select e from Transferencia e " +
            " where cast(e.creadoEn as date) = cast(?1 as date) and ?2 = ?1" +
            "or cast(e.creadoEn as date) >= cast(?1 as date) AND cast(e.creadoEn as date) <= cast(?2 as date) order by e.id desc")
    public List<Transferencia> findByDate(String start, String end);

    @Query(value = "select * from operaciones.transferencia t " +
            "where t.usuario_pre_transferencia_id = ?1 or t.usuario_preparacion_id = ?1 or t.usuario_transporte_id = ?1 or t.usuario_recepcion_id = ?1\n" +
            "order by t.id desc limit 20", nativeQuery = true)
    public List<Transferencia> findByUsuario(Long id);

    @Query("SELECT t FROM Transferencia t WHERE "
            + "(:sucursalOrigenId IS NULL OR t.sucursalOrigen.id = :sucursalOrigenId) AND "
            + "(:sucursalDestinoId IS NULL OR t.sucursalDestino.id = :sucursalDestinoId) AND "
            + "(t.estado = :estado OR cast(:estado as com.franco.dev.domain.operaciones.enums.TransferenciaEstado) IS NULL) AND "
            + "(t.tipo = :tipo or cast(:tipo as com.franco.dev.domain.operaciones.enums.TipoTransferencia) IS NULL) AND "
            + "(t.etapa = :etapa OR cast(:etapa as com.franco.dev.domain.operaciones.enums.EtapaTransferencia) IS NULL) AND "
            + "(:isOrigen IS NULL OR t.isOrigen = :isOrigen) AND "
            + "(:isDestino IS NULL OR t.isDestino = :isDestino) AND "
            + "(t.creadoEn >= :creadoEnDesde or cast(:creadoEnDesde as timestamp) IS NULL) AND "
            + "(t.creadoEn <= :creadoEnHasta or cast(:creadoEnHasta as timestamp) IS NULL ) order by t.id desc")
    Page<Transferencia> findByFilter(
            Long sucursalOrigenId,
            Long sucursalDestinoId,
            TransferenciaEstado estado,
            TipoTransferencia tipo,
            EtapaTransferencia etapa,
            Boolean isOrigen,
            Boolean isDestino,
            LocalDateTime creadoEnDesde,
            LocalDateTime creadoEnHasta,
            Pageable pageable);
}