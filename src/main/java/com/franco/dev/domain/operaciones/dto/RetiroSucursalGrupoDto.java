package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Grupo de devoluciones SEPARADAS de una misma sucursal de origen, listo para
 * el retiro consolidado del proveedor.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroSucursalGrupoDto {
    private Long sucursalId;
    private String sucursalNombre;
    /** Lineas consolidadas por producto+presentacion. */
    private List<RetiroLineaConsolidadaDto> lineas;
    /** Desglose por caja fisica (una entrada por item). */
    private List<RetiroCajaDto> cajas;
    /** Ids de las devoluciones incluidas en este grupo. */
    private List<Long> devolucionIds;
}
