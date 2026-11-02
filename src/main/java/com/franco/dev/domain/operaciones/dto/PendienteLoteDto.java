package com.franco.dev.domain.operaciones.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Una recepcion que entro sin lote («Cargar lote despues») y todavia no se resolvio (#429, fase 2,
 * entrega 3). La clave es la fila SIN LOTE de movimiento_stock_lote (mslId + sucursalId).
 */
@Data
public class PendienteLoteDto {
    private Long mslId;
    private Long sucursalId;
    private String sucursalNombre;
    private Long productoId;
    private String productoDescripcion;
    private Long recepcionId;
    private Long recepcionItemId;
    private String proveedorNombre;
    private LocalDateTime fecha;
    /** Unidades que entraron sin lote en esa recepcion. */
    private Double cantidad;
    /** Lo ya resuelto (atribuido + cerrado sin atribuir) en resoluciones vigentes. */
    private Double resuelto;
    private Double remanente;
}
