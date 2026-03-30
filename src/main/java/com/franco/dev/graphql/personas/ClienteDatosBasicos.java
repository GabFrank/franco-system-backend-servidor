package com.franco.dev.graphql.personas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClienteDatosBasicos {
    String ruc;
    String razonSocial;
    String direccion;
    String estado;
    String estadoContribuyente;
    Boolean tributa;
    Integer tipoContribuyente;
    String telefono;
    String nombreFantasia;
    String dv;
}

