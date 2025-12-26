package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.operaciones.enums.MetodoVerificacion;
import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import com.franco.dev.domain.operaciones.enums.MotivoVerificacionManual;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDefs({
        @TypeDef(name = "motivo_rechazo_fisico", typeClass = PostgreSQLEnumType.class),
        @TypeDef(name = "metodo_verificacion", typeClass = PostgreSQLEnumType.class),
        @TypeDef(name = "motivo_verificacion_manual", typeClass = PostgreSQLEnumType.class),
        @TypeDef(name = "estado_verificacion", typeClass = PostgreSQLEnumType.class)
})
@Table(name = "recepcion_mercaderia_item", schema = "operaciones")
public class RecepcionMercaderiaItem implements Identifiable<Long> {

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
    @JoinColumn(name = "recepcion_mercaderia_id", nullable = false)
    private RecepcionMercaderia recepcionMercaderia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_item_id", nullable = false)
    private NotaRecepcionItem notaRecepcionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_item_distribucion_id")
    private NotaRecepcionItemDistribucion notaRecepcionItemDistribucion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_recibida_id")
    private Presentacion presentacionRecibida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_entrega_id", nullable = false)
    private Sucursal sucursalEntrega;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "cantidad_recibida", nullable = false)
    private Double cantidadRecibida;

    @Column(name = "cantidad_rechazada")
    private Double cantidadRechazada;

    @Column(name = "es_bonificacion")
    private Boolean esBonificacion = false;

    @Column(name = "vencimiento_recibido")
    private LocalDate vencimientoRecibido;

    @Column(name = "lote")
    private String lote;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_rechazo")
    @Type(type = "motivo_rechazo_fisico")
    private MotivoRechazoFisico motivoRechazo;

    @Column(name = "observaciones")
    private String observaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_verificacion")
    @Type(type = "metodo_verificacion")
    private MetodoVerificacion metodoVerificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_verificacion_manual")
    @Type(type = "motivo_verificacion_manual")
    private MotivoVerificacionManual motivoVerificacionManual;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_verificacion", nullable = false)
    @Type(type = "estado_verificacion")
    private EstadoVerificacion estadoVerificacion = EstadoVerificacion.PENDIENTE;

    /**
     * Obtiene el ID de la recepción de mercadería
     * @return ID de la recepción de mercadería
     */
    public Long getRecepcionMercaderiaId() {
        return recepcionMercaderia != null ? recepcionMercaderia.getId() : null;
    }
} 