package com.franco.dev.domain.empresarial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sucursal", schema = "empresarial")
public class Sucursal implements Identifiable<Long> {

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

    private String nombre;

    private String localizacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ciudad_id", nullable = true)
    @JsonIgnore
    private Ciudad ciudad;

    private Boolean deposito;

    @Column(name = "is_configured")
    private Boolean isConfigured;

    @Column(name = "deposito_predeterminado")
    private Boolean depositoPredeterminado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private Usuario usuario;

    private String direccion;

    @Column(name = "nro_delivery")
    private String nroDelivery;

    @Column(name = "codigo_establecimiento_factura", nullable = true)
    private String codigoEstablecimientoFactura;

    private String ip;

    private Integer puerto;

}