package com.franco.dev.domain.dto;

import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MovimientoStockResumenDto {
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Double stockPorRangoFecha;
    private List<StockPorTipoMovimientoDto> stockPorTipoMovimientoList;
    private Producto producto;
    private List<Long> sucursalId;
    private List<TipoMovimiento> tipoMovimientoList;
    private Usuario usuario;

    public MovimientoStockResumenDto(Double stockPorRangoFecha){
        this.stockPorRangoFecha = stockPorRangoFecha;
    }


}
