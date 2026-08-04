package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Saldo disponible de un lote en una sucursal. Es el resultado de agregar el ledger
 * operaciones.movimiento_stock_lote resolviendo los datos contra el maestro operaciones.lote
 * (equivalente a la vista operaciones.v_stock_lote).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLoteDto {
    private Long loteId;
    private Long productoId;
    /** Solo lo completa la consulta con filtros; la query por producto+sucursal lo deja null. */
    private String productoDescripcion;
    private Long sucursalId;
    /** Solo lo completa la consulta con filtros; la query por producto+sucursal lo deja null. */
    private String sucursalNombre;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    /** Fecha por la que ordena FEFO. Null si el producto no tiene días de vencimiento configurados. */
    private LocalDate fechaRetiro;
    /** Solo los LIBERADO se pueden vender. */
    private EstadoLote estado;
    private Double cantidadDisponible;
    /**
     * De quién vino el lote. Solo lo completa la consulta con filtros. Va al final para no correr
     * el orden del constructor de Lombok, del que dependen los llamadores existentes.
     */
    private String proveedorNombre;

    /**
     * Constructor para la consulta FEFO por producto y sucursal, donde el llamador ya conoce el
     * producto y la sucursal y no hace falta resolver sus nombres.
     */
    public StockLoteDto(Long loteId, Long productoId, Long sucursalId, String numeroLote,
                        LocalDate fechaVencimiento, LocalDate fechaRetiro, EstadoLote estado,
                        Double cantidadDisponible) {
        this.loteId = loteId;
        this.productoId = productoId;
        this.sucursalId = sucursalId;
        this.numeroLote = numeroLote;
        this.fechaVencimiento = fechaVencimiento;
        this.fechaRetiro = fechaRetiro;
        this.estado = estado;
        this.cantidadDisponible = cantidadDisponible;
    }
}
