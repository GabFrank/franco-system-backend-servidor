package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import lombok.Data;

@Data
public class ProcesoEtapaInput {
    private Long id;
    private Long pedidoId;
    private ProcesoEtapaTipo tipoEtapa;
    private ProcesoEtapaEstado estadoEtapa;
    private String fechaInicio;
    private String fechaFin;
    private Long usuarioInicioId;
    private String creadoEn;
} 