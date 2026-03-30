package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.NotaRecepcionItemEstado;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class NotaRecepcionItemInput {
    private Long id;
    private Long notaRecepcionId;
    private Long pedidoItemId;
    private Long productoId;
    private Long presentacionEnNotaId;
    private Double cantidadEnNota;
    private Double precioUnitarioEnNota;
    private Boolean esBonificacion;
    private LocalDate vencimientoEnNota;
    private String observacion;
    private NotaRecepcionItemEstado estado;
    private String motivoRechazo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
