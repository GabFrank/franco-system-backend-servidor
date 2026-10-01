package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiquidacionConceptoInput {
    private Long id;
    private String codigo;
    private String descripcion;
    private Boolean esHaber;
    private Boolean esCalculadoAuto;
    private Boolean esRemunerativo;
    private Boolean activo;
    /** Null = no cambia (cliente viejo); 0 = sin número. */
    private Integer numero;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
