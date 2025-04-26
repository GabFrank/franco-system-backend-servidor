package com.franco.dev.domain.personas;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.general.Ciudad;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pre_registro_funcionario", schema = "personas")
public class PreRegistroFuncionario implements Identifiable<Long> {

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
    @JoinColumn(name = "funcionario_id", nullable = true)
    private Funcionario funcionario;

    private String nombreCompleto;
    private String apodo;
    private LocalDateTime fechaNacimiento;
    private LocalDateTime fechaIngreso;
    private String documento;
    private String telefonoPersonal;
    private String telefonoEmergencia;
    private String nombreContactoEmergencia;
    private String direccion;
    private String email;
    private String habilidades;
    private Boolean registroConducir;
    private String nivelEducacion;
    private String observacion;
    private String sucursal;
    private String ciudad;
    private Boolean verificado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}


