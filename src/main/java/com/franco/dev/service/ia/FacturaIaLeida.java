package com.franco.dev.service.ia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Datos de una factura en papel leídos por la IA (respuesta estructurada de OpenAI). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacturaIaLeida {
    private String emisorRuc;
    private String emisorNombre;
    private String receptorRuc;
    private String receptorNombre;
    private String timbrado;
    private String establecimiento;
    private String puntoExpedicion;
    private String numero;
    /** yyyy-MM-dd */
    private String fechaEmision;
    /** "CONTADO" | "CREDITO" | null */
    private String condicion;
    private String moneda;
    private BigDecimal totalGeneral;
    private List<String> dudosos = new ArrayList<>();
    private String error;
    private List<Item> items = new ArrayList<>();

    // Metadata de la llamada (no vienen del modelo)
    private String modelo;
    private Integer tokensEntrada;
    private Integer tokensSalida;
    private String respuestaCruda;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String codigoProveedor;
        private String codigoBarras;
        private String descripcion;
        private String unidadMedida;
        private BigDecimal cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal descuento;
        private BigDecimal totalItem;
        private Integer tasaIva;
        private List<String> dudosos = new ArrayList<>();
    }
}
