package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MontosRetiroPayloadDTO {
    private Double retiroGs;
    private Double retiroRs;
    private Double retiroDs;
}
