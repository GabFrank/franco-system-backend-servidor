package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

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
            "or cast(e.creadoEn as date) >= cast(?1 as date) AND cast(e.creadoEn as date) <= cast(?2 as date)")
    public List<Transferencia> findByDate(String start, String end);

    @Query(value = "select * from operaciones.transferencia t " +
            "where t.usuario_pre_transferencia_id = ?1 or t.usuario_preparacion_id = ?1 or t.usuario_transporte_id = ?1 or t.usuario_recepcion_id = ?1\n" +
            "limit 20", nativeQuery = true)
    public List<Transferencia> findByUsuario(Long id);
}