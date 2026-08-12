package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Operacion de colecta interna (cabecera): traslado de devoluciones separadas
 * de una sucursal de origen a un deposito destino. Una operacion = un origen y
 * un destino (un viaje).
 *
 * Usado en:
 * - Desktop: No (por ahora; la mutation en bloque la crea internamente)
 * - Mobile: Si (historial de colectas)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "colecta_devolucion", schema = "operaciones")
public class ColectaDevolucion implements Identifiable<Long> {

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
    @JoinColumn(name = "sucursal_origen_id", nullable = false)
    private Sucursal sucursalOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_destino_id", nullable = false)
    private Sucursal sucursalDestino;

    @Column(name = "observacion")
    private String observacion;

    /** CONFIRMADO | REVERTIDO */
    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
