package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QrRetiroPreGastoPayloadDTO {
    private String codigoQr;
    private Long preGastoId;
    private Long sucursalId;
    private String qrToken;
}
