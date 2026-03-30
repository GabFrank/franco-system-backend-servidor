package com.franco.dev.service.sifen.config;

import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.SifenConfig;
import com.roshka.sifen.core.SifenConfig.TipoAmbiente;
import com.roshka.sifen.core.SifenConfig.TipoCertificadoCliente;
import com.roshka.sifen.core.exceptions.SifenException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@EnableConfigurationProperties(SifenProperties.class)
@ConditionalOnProperty(name = "sifen.enabled", havingValue = "true", matchIfMissing = false)
public class SifenConfiguration {

    @Bean
    public SifenConfig sifenConfig(SifenProperties properties) throws FileNotFoundException {
        // Validaciones explícitas solo cuando SIFEN está habilitado
        if (!properties.isEnabled()) {
            // Aunque esta clase está condicionada por propiedad, mantenemos la verificación por robustez
            return null;
        }

        if (properties.getCscId() == null || properties.getCscId().trim().isEmpty()) {
            throw new IllegalArgumentException("sifen.cscId es requerido cuando sifen.enabled=true");
        }
        if (properties.getCsc() == null || properties.getCsc().trim().isEmpty()) {
            throw new IllegalArgumentException("sifen.csc es requerido cuando sifen.enabled=true");
        }
        if (properties.getCertificado() == null ||
            properties.getCertificado().getArchivo() == null || properties.getCertificado().getArchivo().trim().isEmpty()) {
            throw new IllegalArgumentException("sifen.certificado.archivo es requerido cuando sifen.enabled=true");
        }
        if (properties.getCertificado().getContrasena() == null || properties.getCertificado().getContrasena().trim().isEmpty()) {
            throw new IllegalArgumentException("sifen.certificado.contrasena es requerido cuando sifen.enabled=true");
        }

        String certPath = properties.getCertificado().getArchivo();

        if (Files.notExists(Paths.get(certPath))) {
            log.error("ARCHIVO DE CERTIFICADO SIFEN NO ENCONTRADO EN: {}", certPath);
            log.error("La aplicación no se iniciará. Verifique la ruta en `application.properties` o la variable de entorno `SIFEN_CERT_PATH`.");
            throw new FileNotFoundException("No se encontró el archivo de certificado SIFEN: " + certPath);
        }


        SifenConfig config = new SifenConfig(
            TipoAmbiente.valueOf(properties.getAmbiente().toUpperCase()),
            properties.getCscId(),
            properties.getCsc(),
            TipoCertificadoCliente.valueOf(properties.getCertificado().getTipo().toUpperCase()),
            certPath,
            properties.getCertificado().getContrasena()
        );

        config.setHabilitarNotaTecnica13(properties.isHabilitarNotaTecnica13());

        // Inicializa la configuración global de la librería
        try {
            Sifen.setSifenConfig(config);
        } catch (SifenException e) {
            log.error("Error al inicializar la configuración de SIFEN: {}", e.getMessage());
            e.printStackTrace();
        }
        return config;
    }

    @Bean
    public Sifen sifen(SifenConfig sifenConfig) {
        return new Sifen();
    }
}

