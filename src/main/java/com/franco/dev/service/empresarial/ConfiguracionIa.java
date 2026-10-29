package com.franco.dev.service.empresarial;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vista de la configuración de IA. Nunca lleva las claves: solo si están cargadas y sus últimos 4 caracteres.
 * claveCargada/claveFinal/modelo son de OpenAI (nombres viejos, se mantienen por compatibilidad).
 */
@Data
@NoArgsConstructor
public class ConfiguracionIa {
    /** Proveedor activo: OPENAI o CLAUDE. */
    private String proveedor;

    private Boolean claveCargada;
    private String claveFinal;
    private String modelo;

    private Boolean claudeClaveCargada;
    private String claudeClaveFinal;
    private String claudeModelo;

    private String promptAdicional;
    private Boolean cifradoDisponible;
}
