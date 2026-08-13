package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.FuncionarioDocumentoTipo;
import lombok.Data;

@Data
public class FuncionarioDocumentoInput {
    private Long id;
    private Long funcionarioId;
    private FuncionarioDocumentoTipo tipo;
    private String nombreArchivo;
    private String contenidoBase64;
    private String mimeType;
    private Long tamanoBytes;
    private String vencimiento;
    private String observacion;
}
