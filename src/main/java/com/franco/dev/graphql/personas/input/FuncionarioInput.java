package com.franco.dev.graphql.personas.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FuncionarioInput {
    private Long id;
    private Long personaId;
    private Long cargoId;
    private Float credito;
    private String fechaIngreso;
    private Float sueldo;
    private Long supervisadoPorId;
    private Boolean fasePrueba;
    private Boolean diarista;
    private Long sucursalId;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Long horarioId;
    private String codigoInterno;
    private Boolean ipsActivo;
    private String numeroIps;
    private String fechaIngresoIps;
    private String cuentaBancaria;
    private String contactoEmergenciaNombre;
    private String contactoEmergenciaTelefono;
}
