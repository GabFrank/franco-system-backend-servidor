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
public class VentaPorSucursal {
    Long sucId;
    String nombre;
    Double total;
    /** Cantidad de ventas (tickets) concluidos en el período. */
    Double cantidadVentas;
    private List<DesglosePeriodoGrafico> desglosePeriodos = new ArrayList<>();
    private List<DesgloseAnhoGrafico> desgloseAnhos = new ArrayList<>();
}
