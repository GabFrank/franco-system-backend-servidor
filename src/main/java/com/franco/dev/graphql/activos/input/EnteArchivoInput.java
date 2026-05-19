package com.franco.dev.graphql.activos.input;

import com.franco.dev.domain.activos.enums.TipoArchivoEnte;
import lombok.Data;

@Data
public class EnteArchivoInput {
    private Long id;
    private Long enteId;
    private TipoArchivoEnte tipoArchivo;
    private String url;
    private String descripcion;
    private Boolean vigente;
    private Long usuarioId;
}
