package com.franco.dev.dto.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidacionFinalizacionRecepcion {
    private Boolean puedeFinalizar;
    private List<ItemPendiente> itemsPendientes;
    private Integer cantidadItemsPendientes;
    private String mensaje;
} 