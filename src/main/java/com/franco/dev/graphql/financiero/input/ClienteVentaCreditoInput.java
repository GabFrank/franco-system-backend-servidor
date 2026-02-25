package com.franco.dev.graphql.financiero.input;

import lombok.Data;
import java.util.List;

@Data
public class ClienteVentaCreditoInput {
    private Long clienteId;
    private List<VentaCreditoInput> ventaCreditoInputList;
}

