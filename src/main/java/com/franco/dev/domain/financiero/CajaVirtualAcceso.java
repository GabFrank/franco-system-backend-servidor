package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Permiso de un usuario sobre una caja virtual.
 *
 * <p>El rol de tesoreria habilita la capacidad (ver / gestionar); esta fila delimita
 * <b>sobre que cajas</b>. Modelo AND: hace falta el rol <i>y</i> el acceso.</p>
 *
 * <p>El propietario de la caja ({@code caja_virtual.usuario_id}) no lleva fila: tiene lectura
 * y escritura implicitas y es quien administra la lista. Un superusuario (rol o nickname
 * ADMIN) tampoco: pasa por encima de todo.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "caja_virtual_acceso", schema = "financiero")
public class CajaVirtualAcceso implements Serializable {

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
    @JoinColumn(name = "caja_virtual_id", nullable = false)
    private CajaVirtual cajaVirtual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Ver la caja, sus saldos y sus movimientos. */
    @Column(name = "puede_leer")
    private Boolean puedeLeer;

    /** Mover plata: ingresos, egresos, transferencias, anulaciones. Implica lectura. */
    @Column(name = "puede_escribir")
    private Boolean puedeEscribir;

    /** Quien otorgo el acceso (auditoria): el propietario de la caja, o un ADMIN. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "otorgado_por_id", nullable = true)
    private Usuario otorgadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
