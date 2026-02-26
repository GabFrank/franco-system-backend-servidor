package com.franco.dev.fmc.service;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.VentaCredito;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import java.text.DecimalFormat;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public PushNotificationRequest gastoRealizado(Gasto gasto, Sucursal sucursal, DecimalFormat decimalFormat) {
        PushNotificationRequest request = base("GASTO REALIZADO", buildGastoMessage(gasto, sucursal, decimalFormat));
        request.setType("GASTO");
        request.setData("/financiero/gastos/" + (gasto.getId() != null ? gasto.getId() : ""));
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
        request.setType("VENTA_CREDITO_CLIENTE");
        request.setData("/mis-finanzas/list-convenio/" + ventaCredito.getId() + "/" + ventaCredito.getSucursalId());
        return request;
    }

    public PushNotificationRequest ventaConvenioRealizada(Venta venta, Sucursal sucursal, Double valor,
            DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("SE HA DETECTADO UNA VENTA CON CONVENIO EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" POR EL VALOR DE ")
                .append(decimalFormat.format(valor))
                .append(" GS.");
        PushNotificationRequest request = base("VENTA CON CONVENIO REALIZADA", builder.toString());
        request.setType("VENTA_CONVENIO");
        request.setData("/operaciones/ventas/" + venta.getId() + "/" + venta.getSucursalId());
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

    public PushNotificationRequest ajusteCosto(Producto producto,
            Sucursal sucursal,
            Double nuevoCosto,
            Double costoAnterior,
            Double ultimoPrecioCompra,
            Usuario usuario,
            DecimalFormat decimalFormat) {
        if (producto == null || nuevoCosto == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SE AJUSTÓ EL COSTO DEL PRODUCTO ")
                .append(producto.getDescripcion() != null ? producto.getDescripcion() : "#" + producto.getId());
        if (sucursal != null && sucursal.getNombre() != null) {
            builder.append(" EN LA SUCURSAL ").append(sucursal.getNombre());
        }
        builder.append(". NUEVO COSTO: ").append(formatGs(nuevoCosto, decimalFormat));
        if (costoAnterior != null) {
            builder.append(" | ANTES: ").append(formatGs(costoAnterior, decimalFormat));
        }
        if (ultimoPrecioCompra != null) {
            builder.append(" | ÚLTIMA COMPRA: ").append(formatGs(ultimoPrecioCompra, decimalFormat));
        }
        if (usuario != null && usuario.getNickname() != null) {
            builder.append(". RESPONSABLE: ").append(usuario.getNickname());
        }

        PushNotificationRequest request = base("AJUSTE DE COSTO REALIZADO", builder.toString());
        request.setType("AJUSTE_COSTO");
        request.setData("/productos/" + (producto != null && producto.getId() != null ? producto.getId() : ""));
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
        request.setData("/productos/" + (producto.getId() != null ? producto.getId() : ""));
        return request;
    }

    public PushNotificationRequest precioVentaActualizado(Producto producto,
            Presentacion presentacion,
            TipoPrecio tipoPrecio,
            Double nuevoPrecio,
            Double precioAnterior,
            Usuario usuario,
            Sucursal sucursal,
            DecimalFormat decimalFormat) {
        if (producto == null || nuevoPrecio == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SE ACTUALIZÓ EL PRECIO DE VENTA");
        if (producto.getDescripcion() != null) {
            builder.append(" DEL PRODUCTO ").append(producto.getDescripcion());
        }
        if (presentacion != null && presentacion.getDescripcion() != null) {
            builder.append(" | PRESENTACIÓN: ").append(presentacion.getDescripcion());
        }
        if (tipoPrecio != null && tipoPrecio.getDescripcion() != null) {
            builder.append(" | TIPO: ").append(tipoPrecio.getDescripcion());
        }
        builder.append(". NUEVO PRECIO: ").append(formatGs(nuevoPrecio, decimalFormat));
        if (precioAnterior != null) {
            builder.append(" | ANTERIOR: ").append(formatGs(precioAnterior, decimalFormat));
        }
        if (sucursal != null && sucursal.getNombre() != null) {
            builder.append(" | SUCURSAL: ").append(sucursal.getNombre());
        }
        if (usuario != null && usuario.getNickname() != null) {
            builder.append(" | ACTUALIZADO POR: ").append(usuario.getNickname());
        }

        PushNotificationRequest request = base("PRECIO ACTUALIZADO", builder.toString());
        request.setType("PRECIO_ACTUALIZADO");
        request.setData("/productos/" + (producto.getId() != null ? producto.getId() : ""));
        return request;
    }

    public PushNotificationRequest transferenciaIniciada(Transferencia transferencia, Sucursal sucursalOrigen,
            Sucursal sucursalDestino) {
        if (transferencia == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("SE HA INICIADO UNA NUEVA TRANSFERENCIA");
        if (sucursalOrigen != null && sucursalOrigen.getNombre() != null) {
            builder.append(" DESDE ").append(sucursalOrigen.getNombre());
        }
        if (sucursalDestino != null && sucursalDestino.getNombre() != null) {
            builder.append(" HACIA ").append(sucursalDestino.getNombre());
        }
        if (transferencia.getUsuarioPreTransferencia() != null &&
                transferencia.getUsuarioPreTransferencia().getNickname() != null) {
            builder.append(". INICIADA POR: ").append(transferencia.getUsuarioPreTransferencia().getNickname());
        }

        PushNotificationRequest request = base("TRANSFERENCIA INICIADA", builder.toString());
        request.setType("TRANSFERENCIA_INICIADA");
        request.setData("/operaciones/transferencias/" + transferencia.getId());
        return request;
    }

    public PushNotificationRequest inventarioIniciado(String tipoInventario, String sucursalNombre,
            String usuarioNombre, Long inventarioId) {
        StringBuilder builder = new StringBuilder();
        builder.append("SE HA INICIADO UN NUEVO INVENTARIO");

        if (tipoInventario != null && !tipoInventario.isEmpty()) {
            builder.append(" TIPO ").append(tipoInventario);
        }

        if (sucursalNombre != null && !sucursalNombre.isEmpty()) {
            builder.append(" EN ").append(sucursalNombre.toUpperCase());
        }

        if (usuarioNombre != null && !usuarioNombre.isEmpty()) {
            builder.append(". RESPONSABLE: ").append(usuarioNombre.toUpperCase());
        }

        PushNotificationRequest request = base("INVENTARIO INICIADO", builder.toString());
        request.setType("INVENTARIO_INICIADO");
        request.setData("/inventario/" + (inventarioId != null ? inventarioId : ""));
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
        builder.append("SE HA DETECTADO UN GASTO EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" POR EL VALOR DE ");
        if (gasto.getRetiroGs() != null && gasto.getRetiroGs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroGs())).append(" Gs. ");
        }
        if (gasto.getRetiroRs() != null && gasto.getRetiroRs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroRs())).append(" Rs. ");
        }
        if (gasto.getRetiroDs() != null && gasto.getRetiroDs() > 0) {
            builder.append(decimalFormat.format(gasto.getRetiroDs())).append(" Ds. ");
        }
        if (gasto.getUsuario() != null) {
            builder.append("REALIZADO POR: ")
                    .append(gasto.getUsuario().getNickname() != null ? gasto.getUsuario().getNickname().toUpperCase()
                            : "");
        }
        return builder.toString();
    }

    public PushNotificationRequest ventaCreditoRealizadaCliente(VentaCredito ventaCredito, Sucursal sucursal,
            DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("REALIZASTE UNA COMPRA CON TU CONVENIO EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" POR ")
                .append(decimalFormat.format(ventaCredito.getValorTotal()))
                .append(" GS HA SIDO REGISTRADA EXITOSAMENTE.");
        PushNotificationRequest request = base("COMPRA A CRÉDITO REGISTRADA", builder.toString());
        request.setType("VENTA_CREDITO");
        request.setData("/mis-compras/credito/" + ventaCredito.getId() + "/" + ventaCredito.getSucursalId());
        return request;
    }

    public PushNotificationRequest facturaAltoValor(Long facturaId, Long sucursalId, Sucursal sucursal,
            Double valorTotal, String clienteNombre, DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("SE HA EMITIDO UNA FACTURA DE ALTO VALOR EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" POR EL VALOR DE ")
                .append(decimalFormat.format(valorTotal))
                .append(" GS. CLIENTE: ")
                .append(clienteNombre != null ? clienteNombre : "N/A");
        PushNotificationRequest request = base("FACTURA DE ALTO VALOR EMITIDA", builder.toString());
        request.setType("FACTURA_ALTO_VALOR");
        request.setData("/operaciones/facturas/" + facturaId + "/" + sucursalId);
        return request;
    }

    public PushNotificationRequest cambioSucursalPreTransferencia(Transferencia transferencia,
            Sucursal sucursalAnterior,
            Sucursal sucursalNueva, boolean esOrigen) {
        StringBuilder builder = new StringBuilder();
        builder.append("CAMBIO DE SUCURSAL EN PRE-TRANSFERENCIA").append(transferencia.getId());
        builder.append(". ");
        builder.append(esOrigen ? "ORIGEN" : "DESTINO");
        builder.append(" CAMBIADO DE ");
        builder.append(sucursalAnterior != null ? sucursalAnterior.getNombre() : "N/A");
        builder.append(" A ");
        builder.append(sucursalNueva != null ? sucursalNueva.getNombre() : "N/A");
        builder.append(".");

        if (transferencia.getUsuarioPreTransferencia() != null) {
            builder.append(" USUARIO: ").append(transferencia.getUsuarioPreTransferencia().getNickname());
        }

        PushNotificationRequest request = base("CAMBIO DE SUCURSAL EN TRANSFERENCIA", builder.toString());
        request.setType("CAMBIO_SUCURSAL_PRE_TRANSFERENCIA");
        request.setData("/operaciones/transferencias/" + transferencia.getId());
        return request;
    }

    public PushNotificationRequest nuevoDispositivoDetectado(String tipoDispositivo, String nombreSucursal) {
        StringBuilder builder = new StringBuilder();
        builder.append("ALERTA DE SEGURIDAD: SE HA DETECTADO UN INICIO DE SESIÓN DESDE UN NUEVO DISPOSITIVO ");
        builder.append(tipoDispositivo != null ? tipoDispositivo : "DESCONOCIDO");
        if (nombreSucursal != null) {
            builder.append(" en la sucursal ").append(nombreSucursal);
        }
        builder.append(". ¿FUISTE TÚ? SI NO RECONOCE ESTA ACTIVIDAD, CAMBIA TU CONTRASEÑA INMEDIATAMENTE.");

        PushNotificationRequest request = base("NUEVO DISPOSITIVO DETECTADO", builder.toString());
        request.setType("NUEVO_DISPOSITIVO");
        request.setData("/configuracion/seguridad");
        return request;
    }

    public PushNotificationRequest cotizacionActualizada(String denominacionMoneda, String simboloMoneda,
            Double valorEnGs) {
        if (denominacionMoneda == null || valorEnGs == null) {
            return null;
        }

        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.US);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
        String valorFormateado = formatter.format(valorEnGs);

        StringBuilder builder = new StringBuilder();
        builder.append(denominacionMoneda);
        if (simboloMoneda != null && !simboloMoneda.isEmpty()) {
            builder.append(" (").append(simboloMoneda).append(")");
        }
        builder.append(": Gs. ").append(valorFormateado);

        PushNotificationRequest request = base("ACTUALIZACIÓN DE COTIZACIÓN", builder.toString());
        request.setType("COTIZACION_ACTUALIZADA");
        request.setData("list-cotizacion");
        return request;
    }

    private String formatGs(Double valor, DecimalFormat decimalFormat) {
        if (valor == null) {
            return "-";
        }
        DecimalFormat formatter = decimalFormat != null ? decimalFormat : new DecimalFormat("#,###.##");
        return formatter.format(valor) + " Gs";
    }

    public PushNotificationRequest retiroRealizado(Retiro retiro, Sucursal sucursal) {
        StringBuilder builder = new StringBuilder();
        builder.append("SE HA REALIZADO UN RETIRO EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "");

        if (retiro.getUsuario() != null) {
            builder.append(" REALIZADO POR: ")
                    .append(retiro.getUsuario().getNickname() != null ? retiro.getUsuario().getNickname().toUpperCase()
                            : "");
        }
        PushNotificationRequest request = base("RETIRO REALIZADO", builder.toString());
        request.setType("RETIRO");
        request.setData("/financiero/retiros/" + (retiro.getId() != null ? retiro.getId() : ""));
        return request;
    }

    public PushNotificationRequest ventaTransferenciaRealizada(Venta venta, Sucursal sucursal, Double valorTotal,
            DecimalFormat decimalFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("SE HA REGISTRADO UN PAGO CON TRANSFERENCIA EN LA SUCURSAL ")
                .append(sucursal != null ? sucursal.getNombre() : "")
                .append(" VENTA ID: ")
                .append(venta.getId())
                .append(" POR EL VALOR DE ")
                .append(decimalFormat.format(valorTotal))
                .append(" GS.");

        if (venta.getUsuario() != null) {
            builder.append(" REALIZADO POR: ")
                    .append(venta.getUsuario().getNickname() != null ? venta.getUsuario().getNickname().toUpperCase()
                            : "");
        }

        PushNotificationRequest request = base("PAGO CON TRANSFERENCIA REGISTRADO", builder.toString());
        request.setType("VENTA_TRANSFERENCIA");
        request.setData("/operaciones/ventas/" + venta.getId() + "/" + venta.getSucursalId());
        return request;
    }
}
