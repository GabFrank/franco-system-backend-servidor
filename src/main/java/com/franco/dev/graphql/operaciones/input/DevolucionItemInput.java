package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

@Data
public class DevolucionItemInput {
    private Long id;
    private Long devolucionId;
    private Long productoId;
    private Long presentacionId;
    private Long motivoAveriaId;
    private Long recepcionMercaderiaItemId;
    private Double cantidad;
    private String motivo;
    private String lote;
    private String vencimiento;
    private Double costoUnitario;
    private Double cantidadReingresada;
    private String vencimientoReingreso;
    private String creadoEn;
}
