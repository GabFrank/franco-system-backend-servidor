package com.franco.dev.graphql.rrhh.input;

import lombok.Data;

@Data
public class JustificativoInput {
    private Long id;
    private Long funcionarioId;
    private String fecha;
    /** id del TipoJustificativo (catalogo), ya no un enum */
    private Long tipoId;
    private Long jornadaId;
    private Long sucursalId;
    private String observacion;
    /** id del FuncionarioDocumento adjunto; requerido si el tipo lo exige */
    private Long documentoId;
    private Long registradoPorId;
}
