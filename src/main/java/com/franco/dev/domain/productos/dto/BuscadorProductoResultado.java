package com.franco.dev.domain.productos.dto;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.TipoCoincidenciaBuscador;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BuscadorProductoResultado {
    private Producto producto;
    private String codigoCoincidente;
    private TipoCoincidenciaBuscador tipoCoincidencia;
}
