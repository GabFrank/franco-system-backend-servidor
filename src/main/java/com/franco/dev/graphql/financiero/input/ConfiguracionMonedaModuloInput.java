package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import lombok.Data;

/** Siempre crea una fila nueva: una configuracion no se edita, se reemplaza. */
@Data
public class ConfiguracionMonedaModuloInput {
    private ModuloMoneda modulo;
    /** Solo para PDV; null = toda la empresa. */
    private Long sucursalId;
    private Long monedaId;
    /** "yyyy-MM-dd HH:mm" o ISO-8601. Null = ahora. */
    private String vigenteDesde;
}
