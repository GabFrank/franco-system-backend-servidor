package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CobroDetalleInput {
    private Long id;
    private Long cobroId;
    private Long monedaId;
    private Double cambio;
    private Long formaPagoId;
    private Double valor;
    private Boolean descuento;
    private Boolean aumento;
    private Boolean pago;
    private Boolean vuelto;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Long sucursalId;
    private String identificadorTransaccion;
}
