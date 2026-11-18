package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.operaciones.DocumentoRecibidoItem;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.ProveedorCodigoProducto;
import com.franco.dev.repository.productos.ProveedorCodigoProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Lo que se aprende de un renglón de la factura vinculado a un producto: las próximas facturas del proveedor lo
 * reconocen solas. Lo usan el celular («Recordar», y al cargar la nota) y el desktop (cargar un renglón pendiente).
 */
@Component
public class AprendizajeProveedor {

    private final ProveedorCodigoProductoRepository aprendizajeRepository;
    private final ComprasSecurityService seguridad;

    @Autowired
    public AprendizajeProveedor(ProveedorCodigoProductoRepository aprendizajeRepository, ComprasSecurityService seguridad) {
        this.aprendizajeRepository = aprendizajeRepository;
        this.seguridad = seguridad;
    }

    /** Hay con qué aprender: el código del proveedor o, si no trae, la descripción. */
    static boolean sePuedeAprender(DocumentoRecibidoItem r) {
        return !vacio(r.getCodigoProveedor()) || DescripcionProveedor.normalizar(r.getDescripcion()) != null;
    }

    /**
     * La llave es el código si el renglón lo trae y, si no, la descripción normalizada. La descripción se guarda
     * <b>siempre</b>, también con código: así lo aprendido de una factura electrónica sirve para sugerir el producto
     * en una de papel (o un renglón sin código) del mismo proveedor.
     */
    void aprender(Proveedor proveedor, DocumentoRecibidoItem r, Presentacion presentacion, double factor) {
        String codigo = vacio(r.getCodigoProveedor()) ? null : r.getCodigoProveedor().trim();
        String descripcion = DescripcionProveedor.normalizar(r.getDescripcion());
        if (codigo == null && descripcion == null) return;
        ProveedorCodigoProducto a = (codigo != null
                ? aprendizajeRepository.findByProveedorIdAndCodigoProveedor(proveedor.getId(), codigo)
                : aprendizajeRepository.findByProveedorIdAndCodigoProveedorIsNullAndDescripcionNormalizada(
                        proveedor.getId(), descripcion))
                .orElse(null);
        if (a == null) {
            a = new ProveedorCodigoProducto();
            a.setProveedor(proveedor);
            a.setCodigoProveedor(codigo);
            a.setUsos(0);
            a.setCreadoEn(LocalDateTime.now());
            a.setUsuario(seguridad.usuarioActual());
        }
        a.setDescripcionProveedor(r.getDescripcion());
        a.setDescripcionNormalizada(descripcion);
        a.setPresentacion(presentacion);
        a.setFactor(factor);
        a.setUsos((a.getUsos() != null ? a.getUsos() : 0) + 1);
        a.setUltimoUso(LocalDateTime.now());
        aprendizajeRepository.save(a);
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}
