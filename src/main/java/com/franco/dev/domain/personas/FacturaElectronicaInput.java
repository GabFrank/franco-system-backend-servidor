package com.franco.dev.domain.personas;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FacturaElectronicaInput {
    
    // Datos del emisor
    private String rucEmisor;
    private String razonSocialEmisor;
    private String direccionEmisor;
    private String telefonoEmisor;
    private String emailEmisor;
    
    // Datos del receptor
    private String rucReceptor;
    private String razonSocialReceptor;
    private String direccionReceptor;
    private String telefonoReceptor;
    private String emailReceptor;
    
    // Datos del documento
    private String tipoDocumento;
    private String numeroDocumento;
    private String fechaEmision;
    private String moneda;
    private BigDecimal tipoCambio;
    private String condicionVenta;
    private String tipoPago;
    
    // Totales
    private BigDecimal totalGravado10;
    private BigDecimal totalGravado5;
    private BigDecimal totalExento;
    private BigDecimal totalIva10;
    private BigDecimal totalIva5;
    private BigDecimal totalGeneral;
    
    // Items de la factura
    private List<ItemFacturaInput> items;
    
    // Observaciones
    private String observaciones;
    
    // Datos adicionales
    private String codigoEstablecimiento;
    private String codigoPuntoExpedicion;
    private String codigoSucursal;
}

@Data
class ItemFacturaInput {
    private String codigoProducto;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal descuento;
    private BigDecimal subtotal;
    private String tipoImpuesto;
    private BigDecimal porcentajeImpuesto;
    private BigDecimal montoImpuesto;
    private String unidadMedida;
    private String codigoArancelario;
    private String codigoNandina;
}
