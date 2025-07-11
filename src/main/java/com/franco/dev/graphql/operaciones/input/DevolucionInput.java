package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import lombok.Data;

import java.util.List;

@Data
public class DevolucionInput {
    private Long id;
    private Long proveedorId;
    private Long sucursalOrigenId;
    private String fecha;
    private String motivo;
    private DevolucionEstado estado;
    private Long usuarioId;
    private String creadoEn;
    
    // Para la creación con ítems relacionados
    private List<DevolucionItemInput> items;
} 