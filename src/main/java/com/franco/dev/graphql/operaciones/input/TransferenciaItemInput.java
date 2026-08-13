package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.enums.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import lombok.Data;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransferenciaItemInput {
    private Long id;
    private Long transferenciaId;
    private Long presentacionPreTransferenciaId;
    private Long presentacionPreparacionId;
    private Long presentacionTransporteId;
    private Long presentacionRecepcionId;
    private Double cantidadPreTransferencia;
    private Double cantidadPreparacion;
    private Double cantidadTransporte;
    private Double cantidadRecepcion;
    private String observacionPreTransferencia;
    private String observacionPreparacion;
    private String observacionTransporte;
    private String observacionRecepcion;
    private String vencimientoPreTransferencia;
    private String vencimientoPreparacion;
    private String vencimientoTransporte;
    private String vencimientoRecepcion;
    private TransferenciaItemMotivoModificacion motivoModificacionPreTransferencia;
    private TransferenciaItemMotivoModificacion motivoModificacionPreparacion;
    private TransferenciaItemMotivoModificacion motivoModificacionTransporte;
    private TransferenciaItemMotivoModificacion motivoModificacionRecepcion;
    private TransferenciaItemMotivoRechazo motivoRechazoPreTransferencia;
    private TransferenciaItemMotivoRechazo motivoRechazoPreparacion;
    private TransferenciaItemMotivoRechazo motivoRechazoTransporte;
    private TransferenciaItemMotivoRechazo motivoRechazoRecepcion;
    private Boolean activo;
    private Boolean poseeVencimiento;
    private String creadoEn;
    private Long usuarioId;
    private Boolean vencimientoVerificado;

    /**
     * Lotes elegidos a mano para este item.
     *
     * Semantica deliberada, para no romper a los clientes que no saben de lotes:
     *   - null  -> no tocar la asignacion existente. Es lo que manda todo el codigo anterior.
     *   - []    -> borrar la asignacion de esa etapa; el item vuelve a resolverse por FEFO.
     *   - lista -> reemplazar la asignacion de esa etapa.
     */
    private List<TransferenciaItemLoteInput> lotesAsignados;

    /** Etapa a la que corresponde {@link #lotesAsignados}. Se ignora si la lista viene null. */
    private EtapaAsignacionLote etapaAsignacionLote;

}