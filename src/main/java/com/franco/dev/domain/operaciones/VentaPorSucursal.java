package com.franco.dev.domain.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaPorSucursal {
    Long sucId;
    String nombre;
    Double total;
}
