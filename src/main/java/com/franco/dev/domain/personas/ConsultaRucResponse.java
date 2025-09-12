package com.franco.dev.domain.personas;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConsultaRucResponse {
    private boolean procesamientoCorrecto;
    private String codigoRespuesta;
    private String mensajeRespuesta;
    private String timestamp = LocalDateTime.now().toString();
    private String ruc;
    private String razonSocial;
    private String estadoContribuyente;
    private String codigoEstadoContribuyente;
    private String esFacturadorElectronico;
}
