package com.franco.dev.security;

import lombok.Data;

@Data
public class BiometricLoginRequest {
    private String biometricToken;
    private String idDispositivo;
}
