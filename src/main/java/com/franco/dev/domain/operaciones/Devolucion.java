package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
import com.franco.dev.domain.personas.Proveedor;
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
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDefs({
        @TypeDef(name = "devolucion_estado", typeClass = PostgreSQLEnumType.class),
        @TypeDef(name = "tipo_devolucion", typeClass = PostgreSQLEnumType.class),
        @TypeDef(name = "tipo_resolucion_devolucion", typeClass = PostgreSQLEnumType.class)
})
@Table(name = "devolucion", schema = "operaciones")
public class Devolucion implements Identifiable<Long> {

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

    /** Tipo de documento: con o sin devolucion a proveedor. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type(type = "tipo_devolucion")
    private TipoDevolucion tipo;

    /** Nullable: solo aplica a CON_PROVEEDOR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = true)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_origen_id", nullable = false)
    private Sucursal sucursalOrigen;

    /**
     * Sucursal donde esta fisicamente la mercaderia ahora. Por defecto = origen;
     * la colecta interna la cambia al deposito. El retiro consolidado agrupa por
     * esta sucursal (no por la de origen).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_ubicacion_id")
    private Sucursal sucursalUbicacion;

    /** Momento de la colecta interna (paso a COLECTADO). */
    @Column(name = "colectado_en")
    private LocalDateTime colectadoEn;

    /** Operacion de colecta interna a la que pertenece (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colecta_id")
    private ColectaDevolucion colecta;

    /** Operacion de retiro a proveedor a la que pertenece (nullable). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retiro_id")
    private RetiroDevolucion retiro;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "motivo")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Type(type = "devolucion_estado")
    private DevolucionEstado estado;

    /** Identificador fisico de la caja/lote de devolucion (autogenerado, imprimible). */
    @Column(name = "identificador")
    private String identificador;

    /** Forma de resolucion (solo CON_PROVEEDOR): nota de credito o canje. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resolucion")
    @Type(type = "tipo_resolucion_devolucion")
    private TipoResolucionDevolucion resolucion;

    /** Numero de nota de credito del proveedor (cuando resolucion = NOTA_CREDITO). */
    @Column(name = "nro_nota_credito")
    private String nroNotaCredito;

    /** Monto acreditado por el proveedor (cuando resolucion = NOTA_CREDITO). */
    @Column(name = "monto_acreditado")
    private Double montoAcreditado;

    /** Gasto generado (tipo SIN_PROVEEDOR). FK compuesta de financiero.gasto (id + sucursal_id). */
    @Column(name = "gasto_id")
    private Long gastoId;

    @Column(name = "gasto_sucursal_id")
    private Long gastoSucursalId;

    /**
     * Caja virtual opcional a la que se imputa el egreso del gasto (modulo caja mayor, fd-93).
     * Sin FK: la tabla financiero.caja_virtual puede no existir en develop hasta que fd-93 se mergee.
     */
    @Column(name = "caja_virtual_id")
    private Long cajaVirtualId;

    @Column(name = "observacion")
    private String observacion;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Column(name = "finalizado")
    private Boolean finalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
} 