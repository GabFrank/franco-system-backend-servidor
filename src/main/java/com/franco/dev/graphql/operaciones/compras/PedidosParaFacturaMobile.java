package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Lo que dice el QR escaneado desde el botón universal: de qué proveedor es y qué pedidos suyos esperan factura. */
@Data
public class PedidosParaFacturaMobile {
    private String cdc;
    private String tipoDocumentoDescripcion;
    private String numeroCompleto;
    private String proveedorRuc;
    private Long proveedorId;
    private String proveedorNombre;
    private List<CompraPedidoResumenMobile> pedidos = new ArrayList<>();
}
