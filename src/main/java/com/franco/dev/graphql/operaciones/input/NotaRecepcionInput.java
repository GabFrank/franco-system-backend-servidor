package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.NotaRecepcionEstado;
import lombok.Data;

@Data
public class NotaRecepcionInput {
    private Long id;
    private Long pedidoId;
    private Long compraId;
    private Long documentoId;
    private Integer numero;
    private String tipoBoleta;
    private Integer timbrado;
    private String fecha;
    private Long monedaId;
    private Double cotizacion;
    private NotaRecepcionEstado estado;
    private Boolean pagado;
    private String creadoEn;
    private Long usuarioId;
    private Boolean assignAllItems;
}
