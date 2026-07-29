package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.operaciones.enums.EtapaAsignacionLote;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lote asignado a mano a un item de transferencia, con los datos del maestro ya resueltos.
 *
 * Se devuelve este DTO y no la entidad para que la pantalla no tenga que navegar la relacion con
 * {@code Lote} solo para mostrar el vencimiento o el estado.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferenciaItemLoteDto {
    private Long id;
    private Long loteId;
    private String numeroLote;
    private Double cantidad;
    private EtapaAsignacionLote etapa;
    private LocalDate fechaVencimiento;
    /** Fecha por la que ordena FEFO. Null si el producto no tiene dias de vencimiento configurados. */
    private LocalDate fechaRetiro;
    private EstadoLote estadoLote;
    private Usuario usuario;
    private LocalDateTime creadoEn;
}
