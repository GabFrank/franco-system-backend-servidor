package com.franco.dev.domain.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
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
 * Sucursal adicional que cubre una caja virtual.
 *
 * <p>Una caja cubre su propia sucursal ({@code caja_virtual.sucursal_id}, que no lleva fila) más las de
 * esta tabla. Hoy lo usa el ingreso de retiros del PDV del rol acotado: un gerente a cargo de varias
 * sucursales los recibe todos en una sola caja.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "caja_virtual_sucursal", schema = "financiero")
public class CajaVirtualSucursal implements Serializable {

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
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    /** Quién la agregó (auditoría): el responsable de la caja, o un ADMIN. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agregado_por_id", nullable = true)
    private Usuario agregadoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
