package com.franco.dev.graphql.personas;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConsultaRucResponse {
    Boolean procesamientoCorrecto;
    String codigoRespuesta;
    String mensajeRespuesta;
    String timestamp;
    String ruc;
    String razonSocial;
    String estadoContribuyente;
    String codigoEstadoContribuyente;
    String esFacturadorElectronico;
}
