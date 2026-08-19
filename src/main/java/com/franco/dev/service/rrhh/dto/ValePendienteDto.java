package com.franco.dev.service.rrhh.dto;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.MotivoVale;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila del dialogo "Pagar Vale": un vale pendiente de entrega con su saldo.
 *
 * <p>Es una proyeccion y no la entidad porque el saldo no vive en el vale: sale de su
 * obligacion de pago ({@code SolicitudPago} tipo RRHH), que puede no existir todavia.</p>
 */
@Data
public class ValePendienteDto {
    private Long id;                    // id del vale (es el "N°" que se muestra)
    private LocalDate fecha;
    private BigDecimal monto;
    private BigDecimal saldoPendiente;
    private Boolean esAdelanto;
    private String observacion;
    private Moneda moneda;
    private Funcionario funcionario;
    private String funcionarioNombre;
    private MotivoVale motivo;
    private String motivoDescripcion;
}
