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
    // Datos adicionales del emisor (para precargar/sugerir actualizacion del proveedor)
    private String emisorNombreFantasia;
    private String emisorDireccion;
    private String emisorTelefono;
    private String emisorEmail;
    private String emisorDepartamento;
    private String emisorDistrito;
    private String emisorCiudad;
    private String numeroFactura;
    private String timbrado;
    private String fechaEmision;
    private String moneda;
    private BigDecimal totalGeneral;
    private Boolean esLegal;

    // Control de cuadre de totales (guardrail anti-perdida-silenciosa de items)
    private BigDecimal sumaItems;       // suma de totalItem de los items
    private Boolean totalesCuadran;     // true=cuadra, false=no cuadra, null=no comparable
    private String totalesAdvertencia;  // mensaje cuando no cuadran

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
