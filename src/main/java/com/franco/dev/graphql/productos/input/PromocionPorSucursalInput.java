package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromocionPorSucursalInput {
    private Long id;
    private Long precioId;
    private Long sucursalId;
    private Boolean activo;
    private Boolean esPromocion;
    private String tipoPromocion;
    private LocalDateTime creadoEn;
    private Long usuarioId;
} 