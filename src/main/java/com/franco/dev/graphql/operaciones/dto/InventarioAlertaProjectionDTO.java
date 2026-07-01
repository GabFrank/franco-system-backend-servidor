package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventarioAlertaProjectionDTO {

    private Long productoId;
    private Long presentacionId;
    private LocalDateTime vencimiento;
    private InventarioProductoEstado estado;
}
