package com.franco.dev.domain.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PedidoResumen {

    private Long pedidoId;

    private ProcesoEtapa etapaActual;

    private Long cantidadItems;

    private Double valorTotal;

    // Nuevos campos para distribución
    private Long cantidadItemsConDistribucionCompleta;

    private Long cantidadItemsPendientesDistribucion;
}