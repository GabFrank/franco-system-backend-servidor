package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/**
 * Catalogo de motivos de averia/devolucion (reason codes).
 * Reemplaza el texto libre por un catalogo configurable para reportes por motivo.
 *
 * Usado en:
 * - Desktop: Si (modulo de devoluciones, seleccion de motivo por item)
 * - Mobile: Si (carga de devolucion por escaneo)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "motivo_averia", schema = "operaciones")
public class MotivoAveria implements Identifiable<Long> {

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

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "activo")
    private Boolean activo;

    /** Si true, una devolucion SIN_PROVEEDOR con este motivo genera gasto (merma). */
    @Column(name = "genera_gasto")
    private Boolean generaGasto;

    /** Si true, el motivo aplica a devoluciones CON_PROVEEDOR. */
    @Column(name = "aplica_proveedor")
    private Boolean aplicaProveedor;
}
