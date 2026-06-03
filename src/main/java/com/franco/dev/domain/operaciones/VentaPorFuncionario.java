package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
    private List<DesglosePeriodoGrafico> desglosePeriodos = new ArrayList<>();
    private List<DesgloseAnhoGrafico> desgloseAnhos = new ArrayList<>();

    public VentaPorFuncionario(Long id, String funcionario, Double total, Long cantidad) {
        this.id = id;
        this.funcionario = funcionario;
        this.total = total;
        this.cantidad = cantidad;
    }
}
