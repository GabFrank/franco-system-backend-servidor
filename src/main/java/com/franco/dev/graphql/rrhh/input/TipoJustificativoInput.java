package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.DescuentoJustificativo;
import lombok.Data;

@Data
public class TipoJustificativoInput {
    private Long id;
    private String nombre;
    private String descripcion;
    private Boolean evitaPenalizacion;
    private DescuentoJustificativo descuentaSalario;
    private Boolean requiereDocumento;
    private Boolean generadoPorSistema;
    private Boolean activo;
    private Long usuarioId;
}
