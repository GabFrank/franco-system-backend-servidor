package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PedidoItemSucursalInput {
    private Long id;
    private Long pedidoItemId;
    private Long sucursalId;
    private Long sucursalEntregaId;
    
    // 📦 Pedido inicial
    private Double cantidadPorUnidad;
    
    // ✅ Recepción efectiva
    private Double cantidadPorUnidadRecibida;
    private Double cantidadPorUnidadRechazada;
    
    // 🛑 Rechazo y modificación
    private Boolean rechazado;
    private String motivoRechazo; // ej: "VENCIDO", "FALTANTE", "AVERÍADO"
    private Boolean modificado;
    private String motivoModificacion; // ej: "PRESENTACION INCORRECTA", "CANTIDAD INCONSISTENTE"
    
    // 👤 Usuario y trazabilidad
    private Long usuarioRecepcionId;
    private LocalDateTime fechaRecepcion;
    
    private Boolean verificado;
    private String observacion;
    
    // Audit fields
    private Long usuarioId;
}
