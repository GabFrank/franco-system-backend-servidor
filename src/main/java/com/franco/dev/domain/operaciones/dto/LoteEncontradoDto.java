package com.franco.dev.domain.operaciones.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un lote que coincide con el numero buscado, con lo necesario para responder un reclamo: de que
 * producto es, de quien vino, cuando entro, donde queda y cuanto se vendio. Parte del MAESTRO, asi
 * que aparece aunque el saldo sea cero o este archivado.
 */
@Data
@NoArgsConstructor
public class LoteEncontradoDto {
    private Long loteId;
    private Long productoId;
    private String productoDescripcion;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    private String estado;
    private String proveedorNombre;
    private Boolean archivado;
    /** Primer movimiento que sumo al lote (normalmente la COMPRA que lo trajo). */
    private LocalDateTime primeraEntradaFecha;
    private String primeraEntradaTipo;
    /** Documento de esa entrada: la recepcion, la transferencia o el ajuste. */
    private Long primeraEntradaDocumentoId;
    private Double saldoTotal;
    /** Unidades vendidas del lote (movimientos VENTA activos), en positivo. */
    private Double totalVendido;
    private Long cantidadVentas;
}
