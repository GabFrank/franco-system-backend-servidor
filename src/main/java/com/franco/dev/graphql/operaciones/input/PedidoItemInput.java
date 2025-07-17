package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PedidoItemInput {
    private Long id;
    private Long productoId;
    private Long pedidoId;
    private Double precioUnitarioSolicitado;
    private String observacion;
    private String vencimientoEsperado;
    private Long presentacionCreacionId;
    private Double cantidadSolicitada;
    private String creadoEn;
    private Long usuarioCreacionId;
    private Boolean esBonificacion;
    private PedidoItemEstado estado;
}