package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista consolidada del retiro de devoluciones (estado SEPARADO) de un proveedor,
 * agrupada por sucursal de origen.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroProveedorConsolidadoDto {
    private Long proveedorId;
    private String proveedorNombre;
    private LocalDateTime fecha;
    private List<RetiroSucursalGrupoDto> grupos;
}
