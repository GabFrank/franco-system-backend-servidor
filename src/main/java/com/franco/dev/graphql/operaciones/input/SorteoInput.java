package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.List;

@Data
public class SorteoInput {
    private Long id;
    private String nombre;
    private String descripcion;
    private Double montoMinimo;
    /** Nombre de {@code ModoParticipacionSorteo}. */
    private String modoParticipacion;
    private Integer cantidadFija;
    private Integer topePorVenta;
    private Boolean incluyeTarjeta;
    /** yyyy-MM-dd o null. */
    private String fechaDesde;
    private String fechaHasta;
    private String fechaSorteo;
    private Boolean todasSucursales;
    private List<Long> sucursalIds;
    private List<SorteoPremioInput> premios;
}
