package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import lombok.Data;

@Data
public class CajaVirtualInput {
    private Long id;
    private String nombre;
    private CajaVirtualTipo tipo;
    private Long sucursalId;
    private Long responsableId;
    private Long usuarioId;
    private Double saldoGs;
    private Double saldoRs;
    private Double saldoDs;
    private Double limiteGs;
    private String descripcion;
    private Boolean activo;
}
