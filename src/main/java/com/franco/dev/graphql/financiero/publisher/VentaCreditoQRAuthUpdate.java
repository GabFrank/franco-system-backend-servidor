package com.franco.dev.graphql.financiero.publisher;

import lombok.Data;

@Data
public class VentaCreditoQRAuthUpdate {

    private Long clienteId;

    private String timestamp;

    private Long sucursalId;

    private String secretKey;
}
