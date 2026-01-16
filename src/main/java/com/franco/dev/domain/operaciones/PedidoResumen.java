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

    private Double valorTotal; // Valor total de las notas de recepción (facturas recibidas)

    private Double valorTotalPedido; // Valor total del pedido (suma de items × precios)

    // Nuevos campos para distribución
    private Long cantidadItemsConDistribucionCompleta;

    private Long cantidadItemsPendientesDistribucion;
}