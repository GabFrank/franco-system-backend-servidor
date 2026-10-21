package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Lo que el comprador tiene que saber antes de finalizar la conciliación. */
@Data
public class ResumenFinalizarNotasMobile {
    private Long pedidoId;
    private Boolean puedeFinalizar;
    private String motivo;
    private Integer cantidadNotas;
    private Double totalFacturado;
    private Integer notasPendientes;
    private List<CompraPedidoItemMobile> itemsSinFacturar = new ArrayList<>();
    private List<String> avisos = new ArrayList<>();
}
