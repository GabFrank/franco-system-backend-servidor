package com.franco.dev.domain.operaciones;

import lombok.Data;

@Data
public class VentaPorSucursal {
    Long sucId;
    String nombre;
    Double total;
}
