package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.productos.Presentacion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Linea del preview de acreditacion: un producto consolidado (a traves de todas
 * las devoluciones del retiro). Cantidad y costo se expresan en unidad base;
 * el desktop permite cambiar la presentacion y editar cantidad/costo/total.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AcreditacionPreviewLineaDto {
    private Long productoId;
    private String productoDescripcion;
    /** Cantidad total en unidad base (suma de item.cantidad * factor). */
    private Double cantidadBase;
    /** Costo medio actual del producto, por unidad base. */
    private Double costoMedio;
    /** cantidadBase * costoMedio. */
    private Double total;
    /** Presentaciones activas del producto (para el selector del dialogo). */
    private List<Presentacion> presentaciones;
}
