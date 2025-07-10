package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.enums.PedidoItemEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pedido_item_sucursal", schema = "operaciones")
public class PedidoItemSucursal implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_item_id", nullable = false)
    private PedidoItem pedidoItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_entrega_id", nullable = true)
    private Sucursal sucursalEntrega;

    // 📦 Pedido inicial
    private Double cantidadPorUnidad;

    // ✅ Recepción efectiva
    private Double cantidadPorUnidadRecibida;
    private Double cantidadPorUnidadRechazada;

    // 🛑 Rechazo y modificación
    private Boolean rechazado = false;
    private String motivoRechazo; // ej: "VENCIDO", "FALTANTE", "AVERÍADO"
    private Boolean modificado = false;
    private String motivoModificacion; // ej: "PRESENTACION INCORRECTA", "CANTIDAD INCONSISTENTE"

    // 👤 Usuario y trazabilidad
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_recepcion_id", nullable = true)
    private Usuario usuarioRecepcion;

    private LocalDateTime fechaRecepcion;

    private Boolean verificado = false;
    private String observacion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
