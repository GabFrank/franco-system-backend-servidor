package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Cliente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClienteResponse {
    Cliente cliente;
    ClienteDatosBasicos datosBasicos;
    List<String> warnings = new ArrayList<>();
    List<String> errores = new ArrayList<>();
    Boolean exito;
}

