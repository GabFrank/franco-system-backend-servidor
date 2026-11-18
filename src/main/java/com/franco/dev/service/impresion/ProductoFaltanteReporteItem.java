package com.franco.dev.service.impresion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.TransferenciaItemMotivoRechazo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.utilitarios.DateUtils;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del reporte de productos faltantes en transferencias (reports/productos-faltantes-transferencia.jrxml). */
@Data
@NoArgsConstructor
public class ProductoFaltanteReporteItem {
    private Long transferenciaId;
    private String fecha;
    private String origen;
    private String destino;
    /** Sin código de barras: lo calcula un resolver con una consulta por fila, caro para miles de filas. */
    private String producto;
    private Double cantidadPedida;
    private Double cantidadPreparada;
    private String etapa;
    private String responsable;
    private String estado;

    /**
     * La etapa del rechazo es la primera, en el orden del flujo, cuyo motivo es el filtrado: el motivo se copia
     * hacia adelante y una etapa posterior lo puede cambiar. Misma regla que la pantalla del desktop.
     */
    public static ProductoFaltanteReporteItem de(TransferenciaItem ti, TransferenciaItemMotivoRechazo motivo) {
        ProductoFaltanteReporteItem r = new ProductoFaltanteReporteItem();
        Transferencia t = ti.getTransferencia();
        Presentacion p = ti.getPresentacionPreTransferencia();
        r.setTransferenciaId(t != null ? t.getId() : null);
        r.setFecha(t != null && t.getCreadoEn() != null
                ? DateUtils.dateToStringWithFormat(t.getCreadoEn(), "dd/MM/yyyy HH:mm") : "-");
        r.setOrigen(t != null ? nombre(t.getSucursalOrigen()) : "-");
        r.setDestino(t != null ? nombre(t.getSucursalDestino()) : "-");
        r.setProducto(p != null && p.getProducto() != null ? p.getProducto().getDescripcion() : "-");
        r.setCantidadPedida(ti.getCantidadPreTransferencia());
        r.setCantidadPreparada(ti.getCantidadPreparacion());
        r.setEstado(t != null && t.getEstado() != null ? t.getEstado().name().replace('_', ' ') : "-");
        if (motivo == ti.getMotivoRechazoPreTransferencia()) {
            r.etapa("Creación", t != null ? t.getUsuarioPreTransferencia() : null);
        } else if (motivo == ti.getMotivoRechazoPreparacion()) {
            r.etapa("Preparación (origen)", t != null ? t.getUsuarioPreparacion() : null);
        } else if (motivo == ti.getMotivoRechazoTransporte()) {
            r.etapa("Transporte", t != null ? t.getUsuarioTransporte() : null);
        } else if (motivo == ti.getMotivoRechazoRecepcion()) {
            r.etapa("Recepción (destino)", t != null ? t.getUsuarioRecepcion() : null);
        } else {
            r.etapa("-", null);
        }
        return r;
    }

    private void etapa(String nombre, Usuario usuario) {
        this.etapa = nombre;
        this.responsable = usuario != null && usuario.getPersona() != null ? usuario.getPersona().getNombre() : "-";
    }

    private static String nombre(Sucursal s) {
        return s != null && s.getNombre() != null ? s.getNombre() : "-";
    }
}
