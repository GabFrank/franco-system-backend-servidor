package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * La cabecera de una factura en papel tal como la leyó la IA, para que el comprador la revise con el papel antes de
 * seguir. {@code dudosos} son los nombres de los campos de los que la IA no estuvo segura (emisorRuc, timbrado…).
 */
@Data
public class CabeceraFacturaFotoMobile {
    private Long documentoId;
    private Long pedidoId;
    private Integer cantidadFotos;
    private String emisorRuc;
    private String emisorNombre;
    private Boolean rucValido;
    private Long proveedorId;
    private String proveedorNombre;
    private String receptorRuc;
    private Boolean receptorEsEmpresa;
    private Integer timbrado;
    private String establecimiento;
    private String puntoExpedicion;
    private String numero;
    /** yyyy-MM-dd */
    private String fechaEmision;
    /** CONTADO | CREDITO */
    private String condicion;
    private String moneda;
    private Double totalGeneral;
    private Double sumaRenglones;
    private Boolean totalesCuadran;
    private Integer cantidadRenglones;
    private List<String> dudosos = new ArrayList<>();
    private List<String> avisos = new ArrayList<>();
}
