package com.franco.dev.dto.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemPendiente {
    private Long itemId;
    private String descripcionProducto;
    private String numeroNota;
    private String motivo;
    private Double cantidadEsperada;
    private Double cantidadRecibida;
    private Double cantidadRechazada;
} 