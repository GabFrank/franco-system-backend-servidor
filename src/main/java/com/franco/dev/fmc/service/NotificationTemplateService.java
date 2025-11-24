package com.franco.dev.fmc.service;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.fmc.model.PushNotificationRequest;
import java.text.DecimalFormat;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public PushNotificationRequest gastoRealizado(Gasto gasto, Sucursal sucursal, DecimalFormat decimalFormat) {
        PushNotificationRequest request = base("Gasto realizado", buildGastoMessage(gasto, sucursal, decimalFormat));
        request.setType("GASTO");
        request.setData("/");
        return request;
    }

    public PushNotificationRequest ventaCreditoRealizada(VentaCredito ventaCredito, Sucursal sucursal,
            DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("SE HA DETECTADO UNA VENTA A CRÉDITO EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" POR EL VALOR DE ")
                .append(decimalFormat.format(ventaCredito.getValorTotal()))
                .append(" GS.");
        PushNotificationRequest request = base("VENTA A CRÉDITO REALIZADA", builder.toString());
        request.setType("VENTA_CREDITO");
        request.setData("/mis-finanzas/list-convenio/" + ventaCredito.getId() + "/" + ventaCredito.getSucursalId());
        return request;
    }

    public PushNotificationRequest manual(String titulo, String mensaje, String data, String tipo) {
        PushNotificationRequest request = base(titulo, mensaje);
        request.setData(data != null ? data : "/");
        request.setType(tipo != null ? tipo : "MANUAL");
        return request;
    }

    public PushNotificationRequest ajusteStock(MovimientoStock movimiento, Sucursal sucursal,
            DecimalFormat decimalFormat) {
        if (movimiento == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SE HA REALIZADO UN AJUSTE DE STOCK");
        if (movimiento.getProducto() != null && movimiento.getProducto().getDescripcion() != null) {
            builder.append(" DEL PRODUCTO ").append(movimiento.getProducto().getDescripcion());
        }
        if (sucursal != null && sucursal.getNombre() != null) {
            builder.append(" EN LA SUCURSAL").append(sucursal.getNombre());
        }
        if (movimiento.getCantidad() != null && decimalFormat != null) {
            String signo = movimiento.getCantidad() >= 0 ? "+" : "";
            builder.append(". CANTIDAD: ").append(signo).append(decimalFormat.format(movimiento.getCantidad()));
        }
        if (movimiento.getUsuario() != null && movimiento.getUsuario().getNickname() != null) {
            builder.append(". REALIZADO POR: ").append(movimiento.getUsuario().getNickname());
        }

        PushNotificationRequest request = base("AJUSTE DE STOCK REALIZADO", builder.toString());
        request.setType("AJUSTE_STOCK");
        request.setData("/operaciones/movimientos-stock");
        return request;
    }

    public PushNotificationRequest productoCreado(Producto producto, Sucursal sucursal) {
        if (producto == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SE HA CREADO UN NUEVO PRODUCTO");
        if (producto.getDescripcion() != null) {
            builder.append(": ").append(producto.getDescripcion());
        }
        if (producto.getSubfamilia() != null && producto.getSubfamilia().getNombre() != null) {
            builder.append(". SUBFAMILIA: ").append(producto.getSubfamilia().getNombre());
        }
        if (sucursal != null && sucursal.getNombre() != null) {
            builder.append(". SUCURSAL: ").append(sucursal.getNombre());
        }

        PushNotificationRequest request = base("NUEVO PRODUCTO CREADO", builder.toString());
        request.setType("PRODUCTO_CREADO");
        request.setData("/productos");
        return request;
    }

    public PushNotificationRequest transferenciaIniciada(Transferencia transferencia, Sucursal sucursalOrigen,
            Sucursal sucursalDestino) {
        if (transferencia == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Se ha iniciado una nueva transferencia");
        if (sucursalOrigen != null && sucursalOrigen.getNombre() != null) {
            builder.append(" desde ").append(sucursalOrigen.getNombre());
        }
        if (sucursalDestino != null && sucursalDestino.getNombre() != null) {
            builder.append(" hacia ").append(sucursalDestino.getNombre());
        }
        if (transferencia.getUsuarioPreTransferencia() != null &&
                transferencia.getUsuarioPreTransferencia().getNickname() != null) {
            builder.append(". Iniciada por: ").append(transferencia.getUsuarioPreTransferencia().getNickname());
        }

        PushNotificationRequest request = base("Transferencia iniciada", builder.toString());
        request.setType("TRANSFERENCIA_INICIADA");
        request.setData("/operaciones/transferencias");
        return request;
    }

    private PushNotificationRequest base(String title, String message) {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setTitle(title);
        request.setMessage(message);
        request.setData("/");
        request.setType("SISTEMA");
        return request;
    }

    private String buildGastoMessage(Gasto gasto, Sucursal sucursal, DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("Se ha detectado un gasto a tu nombre en la sucursal ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" por el valor de ");
        if (gasto.getRetiroGs() != null && gasto.getRetiroGs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroGs())).append(" Gs. ");
        }
        if (gasto.getRetiroRs() != null && gasto.getRetiroRs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroRs())).append(" Rs. ");
        }
        if (gasto.getRetiroDs() != null && gasto.getRetiroDs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroDs())).append(" Ds. ");
        }
        if (sucursal != null && gasto.getUsuario() != null) {
            builder.append("Si desconoce ésta acción contactar con el cajero ")
                    .append(gasto.getUsuario().getNickname() != null ? gasto.getUsuario().getNickname().toUpperCase()
                            : "")
                    .append(" al número ")
                    .append(sucursal.getNroDelivery());
        }
        return builder.toString();
    }
}
