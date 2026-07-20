package com.franco.dev.domain.grafico;

import com.franco.dev.domain.financiero.GastoPorMes;
import com.franco.dev.domain.operaciones.VentaPorMes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IngresoGastoSerieGrafico {
    private Integer anio;
    private Long sucId;
    private String sucursalNombre;
    private List<VentaPorMes> ingresos = new ArrayList<>();
    private List<GastoPorMes> gastos = new ArrayList<>();
}
