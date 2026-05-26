package com.franco.dev.service.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO Jackson para deserializar la respuesta JSON de OpenAI Vision o el parser XML SIFEN.
 * Estructura unificada — ambos pipelines producen este mismo objeto, asi el matcher y el
 * orchestrator no necesitan distinguir el origen.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FacturaIaResponse implements Serializable {

    private String emisorRuc;
    private String emisorNombre;
    private String numeroFactura;
    private String timbrado;
    /** Formato YYYY-MM-DD. Si no es legible, null. */
    private String fechaEmision;
    /** "PYG" o "USD". */
    private String moneda;
    private BigDecimal totalGeneral;
    /** true si tiene timbrado valido y formato de factura legal SIFEN. false si es nota comun. */
    private Boolean esLegal;
    private List<Item> items;
    /** Si la imagen no es factura/nota: "NO_ES_DOCUMENTO_VALIDO" u otro identificador. */
    private String error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item implements Serializable {
        private String codigoProducto;
        private String nombreProducto;
        private BigDecimal cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal descuento;
        private BigDecimal totalItem;
    }
}
