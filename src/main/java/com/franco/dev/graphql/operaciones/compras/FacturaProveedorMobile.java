package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** La factura electrónica del proveedor, con todos sus datos y el cruce contra el pedido. */
@Data
public class FacturaProveedorMobile {
    private Long id;
    private Long pedidoId;
    private String cdc;
    private Integer tipoDocumento;
    private String tipoDocumentoDescripcion;
    private String emisorRuc;
    private String emisorNombre;
    private String receptorRuc;
    private String receptorNombre;
    private Integer timbrado;
    private String establecimiento;
    private String puntoExpedicion;
    private String numero;
    private String numeroCompleto;
    private String fechaEmision;
    private String condicion;
    private String moneda;
    private String monedaSimbolo;
    private Double cotizacion;
    private Double subtotalExenta;
    private Double subtotalIva5;
    private Double subtotalIva10;
    private Double totalDescuento;
    private Double totalGeneral;
    private Double totalIva5;
    private Double totalIva10;
    private Double totalIva;
    /** Σ cantidad base × precio base de lo que va a la nota (sin bonificaciones). */
    private Double totalNota;
    private String consultadaEn;

    private Long proveedorId;
    private String proveedorNombre;

    private Integer renglonesOk;
    private Integer renglonesConDiferencia;
    private Integer renglonesFueraDePedido;
    private Integer renglonesSinProducto;
    private Integer renglonesSinObservacion;
    private Boolean puedeConfirmar;
    private String motivoNoConfirma;

    private Long notaRecepcionId;
    private List<AvisoFacturaMobile> avisos = new ArrayList<>();
    private List<RenglonFacturaMobile> renglones = new ArrayList<>();
    /** Ítems del pedido que esta factura no trae: quedan pendientes para otra factura. */
    private List<CompraPedidoItemMobile> itemsNoFacturados = new ArrayList<>();
}
