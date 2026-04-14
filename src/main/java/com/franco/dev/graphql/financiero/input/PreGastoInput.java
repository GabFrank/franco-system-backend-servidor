package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.EstadoPreGasto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class PreGastoInput {
    private Long id;
    private Long funcionarioId;
    private Long enteId;
    private Long tipoGastoId;
    private String descripcion;
    private Long monedaId;
    private Long sucursalId;
    private BigDecimal montoSolicitado;
    private Long sucursalCajaId;
    private EstadoPreGasto estado;
    private Long autorizadoPorId;
    private Long delegadoAId;
    private String motivoRechazo;
    private BigDecimal montoRetirado;
    private Long usuarioId;
    private Date creadoEn;
    private String nivelUrgencia;
    private String observaciones;
    private Long beneficiarioProveedorId;
    private Long beneficiarioPersonaId;
    private String fechaVencimiento;
    private List<PreGastoDetalleFinanzasInput> finanzas;
}
