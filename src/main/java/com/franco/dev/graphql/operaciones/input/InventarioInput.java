package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.InventarioEstado;
import com.franco.dev.domain.operaciones.enums.TipoInventario;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventarioInput {
    private Long id;
    private Long idCentral;
    private Long idOrigen;
    private Long sucursalId;
    private String fechaInicio;
    private String fechaFin;
    private InventarioEstado estado;
    private TipoInventario tipo;
    private Boolean abierto;
    private String observacion;
    private Long usuarioId;
}
