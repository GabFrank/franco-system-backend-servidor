package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Un renglón de la factura del proveedor con su cruce contra el pedido. */
@Data
public class RenglonFacturaMobile {
    private Long id;
    private Integer orden;
    private String codigoProveedor;
    private String gtin;
    private String gtinPaquete;
    private String descripcion;
    private String unidadMedida;
    private Double cantidad;
    private Double precioUnitario;
    private Double descuentoUnitario;
    private Double porcentajeDescuento;
    private Double total;
    private Integer tasaIva;
    private String ivaTexto;
    private String lote;
    private String vencimiento;
    private Boolean esBonificacion;

    /** OK, DIFERENCIA, FUERA_DE_PEDIDO, SIN_PRODUCTO */
    private String estado;
    private String estadoEtiqueta;
    /** MANUAL, APRENDIDO, CODIGO_BARRAS, CODIGO_PAQUETE, CODIGO_INTERNO */
    private String tipoMatch;
    private String tipoMatchEtiqueta;
    private Long productoId;
    private String productoDescripcion;
    private Long presentacionId;
    private String presentacionDescripcion;
    private Double factor;
    private Long pedidoItemId;

    /** Lo que va a la nota, en unidades base. */
    private Double cantidadBase;
    private Double precioUnitarioBase;
    private Double pedidoCantidadPendiente;
    private Double pedidoPrecioUnitario;

    private List<String> diferencias = new ArrayList<>();
    private List<String> avisosIva = new ArrayList<>();
    private List<String> avisos = new ArrayList<>();
    /** No bloquean, pero el comprador las tiene que mirar: el renglón va a «Para revisar». */
    private List<String> alertas = new ArrayList<>();
    private Boolean requiereObservacion;
    private Boolean observacionFaltante;
    private String observacion;
    /** Solo en SIN_PRODUCTO: el producto no está en el sistema con ese código. */
    private List<SugerenciaProductoMobile> sugerencias = new ArrayList<>();
}
