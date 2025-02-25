package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.Date;

@Data
public class VentaObservacionInput {
    private Long id;
    private String descripcion;
    private String creadoEn;
    private Long motivoObservacionId;
    private Long ventaId;
    private Long sucursalId;
    private Long UsuarioId;

}
