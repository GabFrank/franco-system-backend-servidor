package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventarioProductoItemInput {
    private Long id;
    private Long inventarioProductoId;
    private Long presentacionId;
    private Long zonaId;
    private Double cantidad;
    private Double cantidadFisica;
    private Double cantidadAnterior;
    private LocalDateTime fechaVerificado;
    private Boolean verificado;
    private Boolean revisado;
    private String vencimiento;
    private InventarioProductoEstado estado;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    /**
     * Lote que se esta contando. Opcional y aditivo: el escritorio no lo manda y su renglon sigue
     * entrando sin lote.
     *
     * Con lote, {@code cantidadFisica} es el saldo DE ESE LOTE en la sucursal, no la existencia
     * del producto.
     */
    private Long loteId;
}
