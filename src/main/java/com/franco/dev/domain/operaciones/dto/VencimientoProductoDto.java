package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VencimientoProductoDto {
    private Transferencia transferencia;
    private Presentacion presentacion;
    private Producto producto;
    private Sucursal sucursal;
    private TransferenciaItem transferenciaItem;
}

