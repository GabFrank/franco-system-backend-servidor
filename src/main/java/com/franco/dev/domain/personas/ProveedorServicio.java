package com.franco.dev.domain.personas;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.financiero.CuentaBancaria;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Proveedor de servicios: la empresa que provee las terminales POS y su soporte tecnico.
 *
 * Es independiente de {@link Proveedor}, que modela al proveedor de mercaderia (credito,
 * plazo de cheque, vendedores, productos). Ambos son roles satelite de una {@link Persona},
 * no hay herencia entre ellos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "proveedor_servicio", schema = "personas")
public class ProveedorServicio implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "persona_id", nullable = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @Column(name = "nombre_contacto")
    private String nombreContacto;

    @Column(name = "numero_contacto")
    private String numeroContacto;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
