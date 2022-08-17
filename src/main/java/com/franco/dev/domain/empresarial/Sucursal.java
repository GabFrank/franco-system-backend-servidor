package com.franco.dev.domain.empresarial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sucursal", schema = "empresarial")
public class Sucursal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String localizacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ciudad_id", nullable = true)
    @JsonIgnore
    private Ciudad ciudad;

    private Boolean deposito;

    private Boolean isConfigured;

    @Column
    private Boolean depositoPredeterminado;

    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private Usuario usuario;

    private String direccion;

    private String nroDelivery;

    private String codigoEstablecimientoFactura;

}