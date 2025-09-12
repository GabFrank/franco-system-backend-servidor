package com.franco.dev.service.sifen.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConsultaRucResponse extends SifenResponseBase {
    private String ruc;
    private String razonSocial;
    private String estadoContribuyente;
    private String codigoEstadoContribuyente;
    private String esFacturadorElectronico;
}
