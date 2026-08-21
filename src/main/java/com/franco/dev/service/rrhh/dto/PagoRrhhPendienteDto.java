package com.franco.dev.service.rrhh.dto;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de los dialogos "Pagar Liquidacion" / "Pagar Finiquito" / "Pagar Aguinaldo": un
 * documento de RRHH aprobado y todavia sin pagar, con su saldo.
 *
 * <p>Proyeccion y no la entidad, por el mismo motivo que {@link ValePendienteDto}: el saldo
 * no vive en el documento sino en su obligacion de pago ({@code SolicitudPago} tipo RRHH),
 * que puede no existir todavia. Los tres conceptos comparten forma (funcionario + periodo +
 * monto), asi que comparten DTO y se distinguen por {@code concepto}.</p>
 */
@Data
public class PagoRrhhPendienteDto {
    /** LIQUIDACION | FINIQUITO | AGUINALDO. Define contra que entidad resuelve el id. */
    private String concepto;
    private Long id;
    private LocalDate fecha;
    /** Texto corto que ubica el documento: "2026-07" en liquidacion, "2026" en aguinaldo. */
    private String periodo;
    private BigDecimal monto;
    private BigDecimal saldoPendiente;
    private String descripcion;
    private Moneda moneda;
    private Funcionario funcionario;
    private String funcionarioNombre;
}
