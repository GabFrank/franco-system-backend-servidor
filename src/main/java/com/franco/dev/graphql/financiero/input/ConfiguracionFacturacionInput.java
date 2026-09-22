package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.ModoFacturacion;
import lombok.Data;

/**
 * Reemplazo completo, no PATCH: el dialogo del desktop manda la politica entera. La clave es
 * {@code sucursalId} (NULL = global): guardar sobre una clave que ya tiene fila la actualiza.
 * Sin usuarioId: el autor sale de la sesion.
 */
@Data
public class ConfiguracionFacturacionInput {
    private Long id;
    private Long sucursalId;
    private ModoFacturacion modo;
    private Integer ventasSinFactura;
    private Boolean ventaTicketRespetaPolitica;
}
