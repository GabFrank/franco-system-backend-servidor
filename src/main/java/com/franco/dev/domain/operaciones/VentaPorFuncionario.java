package com.franco.dev.domain.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaPorFuncionario {

    private Long id;
    private String funcionario;
    private Double total;
    private Long cantidad;
    private String productoMasVendido;
    private String sucursales;

    public VentaPorFuncionario(Long id, String funcionario, Double total, Long cantidad) {
        this.id = id;
        this.funcionario = funcionario;
        this.total = total;
        this.cantidad = cantidad;
    }
}
