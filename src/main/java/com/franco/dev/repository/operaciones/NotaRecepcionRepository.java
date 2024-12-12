package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaPedido;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotaRecepcionRepository extends HelperRepository<NotaRecepcion, Long> {
    default Class<NotaRecepcion> getEntityClass() {
        return NotaRecepcion.class;
    }

    public List<NotaRecepcion> findByPedidoId(Long id);

    public Page<NotaRecepcion> findByPedidoId(Long id, Pageable page);

    @Query(
            "SELECT nr FROM NotaRecepcion nr " +
                    "WHERE nr.pedido.id = :id " +
                    "AND (nr.numero IS NULL OR FUNCTION('CAST', nr.numero AS CHAR) LIKE :text) order by nr.id desc"
    )
    Page<NotaRecepcion> findByPedidoIdAndNumero(
            Long id,
            String text,
            Pageable pageable
    );

    @Query( value = "select sum((pi.precio_unitario_recepcion_nota - pi.descuento_unitario_recepcion_nota) * (pi.cantidad_recepcion_nota * p.cantidad)) from operaciones.nota_recepcion nr " +
            "join operaciones.pedido_item pi on nr.id = pi.nota_recepcion_id " +
            "join productos.presentacion p on p.id = pi.presentacion_recepcion_nota_id " +
            "where (pi.cancelado is null or pi.cancelado = false) and nr.id = ?1", nativeQuery = true)
    public Double valor(Long id);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);
}