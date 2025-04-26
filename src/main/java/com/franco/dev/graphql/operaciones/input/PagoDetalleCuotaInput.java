package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PagoDetalleCuotaEstado;
import lombok.Data;

@Data
public class PagoDetalleCuotaInput {
    private Long id;
    private Long pagoDetalleId;
    private Double referenciaId;
    private Double numeroCuota;
    private String fechaVencimiento;
    private PagoDetalleCuotaEstado estado;
    private Double totalPagado;
    private Double totalFinal;
    private String creadoEn;
    private Long usuarioId;
} 