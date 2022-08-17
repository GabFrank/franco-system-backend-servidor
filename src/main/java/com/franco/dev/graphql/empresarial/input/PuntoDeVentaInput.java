package com.franco.dev.graphql.empresarial.input;

import lombok.Data;

@Data
public class PuntoDeVentaInput {
    private Long id;
    private String nombre;
    private String nombreImpresoraTicket;
    private Integer tamanhoImpresoraTicket;
    private Integer nombreImpresoraReportes;
    private Long sucursalId;
    private Long usuarioId;
}
