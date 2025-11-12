package com.franco.dev.service.sifen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sifen")
public class SifenProperties {

    private boolean enabled = true;

    private String ambiente = "DEV"; // Puede ser DEV o PROD

    private Certificado certificado = new Certificado();

    private String csc; // Código de Seguridad del Contribuyente

    private String cscId; // ID del CSC

    private boolean habilitarNotaTecnica13 = false;

    @Data
    public static class Certificado {
        private boolean usar = true;

        private String tipo = "PFX"; // Tipo de certificado, ej: PFX

        private String archivo; // Ruta al archivo del certificado digital

        private String contrasena; // Contraseña del certificado
    }
}

