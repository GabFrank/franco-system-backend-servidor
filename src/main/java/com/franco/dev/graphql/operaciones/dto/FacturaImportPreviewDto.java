package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacturaImportPreviewDto implements Serializable {
    private Long importId;
    private String estado;

    // Cabecera extraida del archivo
    private String emisorRuc;
    private String emisorNombre;
    private String numeroFactura;
    private String timbrado;
    private String fechaEmision;
    private String moneda;
    private BigDecimal totalGeneral;
    private Boolean esLegal;

    // Sugerencia automatica del matcher
    private Proveedor proveedorSugerido;
    private String proveedorConfianza; // HIGH | MEDIUM | NONE
    private String proveedorRazon;

    // Items con su match
    private List<ItemPreview> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ItemPreview implements Serializable {
        private String textoOcr;
        private String codigoOcr;
        private BigDecimal cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal descuento;
        private BigDecimal totalItem;

        // Match
        private Producto productoSugerido;
        private String productoConfianza;
        private String productoRazon;
        private List<Producto> candidatos;
    }
}
