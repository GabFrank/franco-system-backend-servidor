package com.franco.dev.service.sifen.dto.request;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FacturaElectronicaRequest {
    
    // Datos del emisor
    @NotBlank
    private String rucEmisor;
    
    @NotBlank
    private String razonSocialEmisor;
    
    @NotBlank
    private String direccionEmisor;
    
    @NotBlank
    private String telefonoEmisor;
    
    @NotBlank
    private String emailEmisor;
    
    // Datos del receptor
    @NotBlank
    private String rucReceptor;
    
    @NotBlank
    private String razonSocialReceptor;
    
    @NotBlank
    private String direccionReceptor;
    
    @NotBlank
    private String telefonoReceptor;
    
    @NotBlank
    private String emailReceptor;
    
    // Datos del documento
    @NotBlank
    private String tipoDocumento; // 1 = Factura, 2 = Nota de Crédito, 3 = Nota de Débito
    
    @NotBlank
    private String numeroDocumento;
    
    // Tipo de documento electrónico para CDC (por defecto 01 para factura electrónica)
    private String tipoDocumentoElectronico = "01";
    
    @NotNull
    private LocalDateTime fechaEmision;
    
    @NotBlank
    private String moneda; // PYG, USD, etc.
    
    @NotNull
    private BigDecimal tipoCambio;
    
    @NotBlank
    private String condicionVenta; // 1 = Contado, 2 = Crédito
    
    @NotBlank
    private String tipoPago; // 1 = Efectivo, 2 = Cheque, 3 = Tarjeta, 4 = Transferencia
    
    // Totales
    @NotNull
    private BigDecimal totalGravado10;
    
    @NotNull
    private BigDecimal totalGravado5;
    
    @NotNull
    private BigDecimal totalExento;
    
    @NotNull
    private BigDecimal totalIva10;
    
    @NotNull
    private BigDecimal totalIva5;
    
    @NotNull
    private BigDecimal totalGeneral;
    
    // Items de la factura
    @Valid
    @NotNull
    private List<ItemFacturaRequest> items;
    
    // Observaciones
    private String observaciones;
    
    // Datos adicionales
    private String codigoEstablecimiento;
    private String codigoPuntoExpedicion;
    private String codigoSucursal;
}
