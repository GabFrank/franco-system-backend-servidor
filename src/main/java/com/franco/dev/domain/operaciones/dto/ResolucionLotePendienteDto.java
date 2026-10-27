package com.franco.dev.domain.operaciones.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Como se resolvio (o se anulo) un pendiente de lote. */
@Data
public class ResolucionLotePendienteDto {
    private Long id;
    private Long mslId;
    private Long sucursalId;
    private Long productoId;
    private String productoDescripcion;
    private Long loteId;
    private String numeroLote;
    private Double cantidadAtribuida;
    private Double cantidadNoAtribuida;
    private String accion;
    private String motivo;
    private Long movimientoStockId;
    private Long usuarioId;
    private String usuarioNickname;
    private LocalDateTime fecha;
    private Boolean anulada;
}
