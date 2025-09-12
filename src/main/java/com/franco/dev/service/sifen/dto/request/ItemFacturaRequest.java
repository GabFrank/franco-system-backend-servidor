package com.franco.dev.service.sifen.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ItemFacturaRequest {
    
    @NotBlank
    private String codigoProducto;
    
    @NotBlank
    private String descripcion;
    
    @NotNull
    private BigDecimal cantidad;
    
    @NotNull
    private BigDecimal precioUnitario;
    
    @NotNull
    private BigDecimal descuento;
    
    @NotNull
    private BigDecimal subtotal;
    
    @NotBlank
    private String tipoImpuesto; // 1 = Gravado 10%, 2 = Gravado 5%, 3 = Exento
    
    @NotNull
    private BigDecimal porcentajeImpuesto;
    
    @NotNull
    private BigDecimal montoImpuesto;
    
    // Campos adicionales
    private String unidadMedida;
    private String codigoArancelario;
    private String codigoNandina;
}
