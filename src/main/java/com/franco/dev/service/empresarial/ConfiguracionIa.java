package com.franco.dev.service.empresarial;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Vista de la configuración de IA. Nunca lleva la clave: solo si está cargada y sus últimos 4 caracteres. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfiguracionIa {
    private Boolean claveCargada;
    private String claveFinal;
    private String modelo;
    private String promptAdicional;
    private Boolean cifradoDisponible;
}
