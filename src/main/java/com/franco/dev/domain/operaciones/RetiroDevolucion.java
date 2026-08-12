package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Operacion de retiro consolidado a un proveedor (cabecera). Agrupa las
 * devoluciones que se entregaron fisicamente juntas. Es el documento del
 * remito. Una operacion = un proveedor.
 *
 * Usado en:
 * - Desktop: No (por ahora; las mutations en bloque la crean internamente)
 * - Mobile: Si (historial de retiros)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "retiro_devolucion", schema = "operaciones")
public class RetiroDevolucion implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    public static final String ESTADO_CONFIRMADO = "CONFIRMADO";
    public static final String ESTADO_REVERTIDO = "REVERTIDO";

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

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "nro_comprobante")
    private String nroComprobante;

    @Column(name = "observacion")
    private String observacion;

    /** CONFIRMADO | REVERTIDO */
    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
