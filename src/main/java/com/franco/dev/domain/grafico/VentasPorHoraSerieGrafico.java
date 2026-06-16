package com.franco.dev.domain.grafico;

import com.franco.dev.domain.operaciones.VentaPorHora;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentasPorHoraSerieGrafico {
    private Long sucId;
    private String sucursalNombre;
    private String etiqueta;
    private String fecha;
    private List<VentaPorHora> datos = new ArrayList<>();
}
