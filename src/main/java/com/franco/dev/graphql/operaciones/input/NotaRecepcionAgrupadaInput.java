package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.NotaRecepcionAgrupadaEstado;
import lombok.Data;

@Data
public class NotaRecepcionAgrupadaInput {
    private Long id;
    private Long pedidoId;
    private Long proveedorId;
    private Long sucursalId;
    private String creadoEn;
    private Long usuarioId;
    private NotaRecepcionAgrupadaEstado estado;
}
