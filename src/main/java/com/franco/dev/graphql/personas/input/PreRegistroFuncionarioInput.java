package com.franco.dev.graphql.personas.input;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.general.Ciudad;
import lombok.Data;

import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.LocalDateTime;

@Data
public class PreRegistroFuncionarioInput {
    private Long id;
    private Long funcionarioId;
    private String nombreCompleto;
    private String apodo;
    private String fechaNacimiento;
    private String fechaIngreso;
    private String documento;
    private String telefonoPersonal;
    private String telefonoEmergencial;
    private String nombreContactoEmergencia;
    private String direccion;
    private String email;
    private String habilidades;
    private Boolean registroConducir;
    private String nivelEducacion;
    private String observacion;
    private String sucursal;
    private String ciudad;
}

