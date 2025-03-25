package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PagoDetalleEstado;
import lombok.Data;

@Data
public class PagoDetalleInput {
    private Long id;
    private Long pagoId;
    private String creadoEn;
    private Long usuarioId;
    private Long monedaId;
    private Long formaPagoId;
    private Double total;
    private Long sucursalId;
    private Long cajaId;
    private Boolean activo;
    private String fechaProgramado;
    private Boolean plazo;
    private Integer cuotas;
    private PagoDetalleEstado estado;
}

