package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.RecepcionMercaderiaEstado;
import lombok.Data;

import java.util.List;

@Data
public class RecepcionMercaderiaInput {
    private Long id;
    private Long proveedorId;
    private Long sucursalRecepcionId;
    private String fecha;
    private Long monedaId;
    private Double cotizacion;
    private RecepcionMercaderiaEstado estado;
    private Long usuarioId;
    private String creadoEn;
    
    // Para la creación con ítems relacionados
    private List<RecepcionMercaderiaItemInput> items;
    private List<RecepcionCostoAdicionalInput> costosAdicionales;
    private List<Long> notaRecepcionIds;
} 