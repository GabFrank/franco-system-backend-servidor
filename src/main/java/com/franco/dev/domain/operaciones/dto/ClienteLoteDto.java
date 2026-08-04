package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un cliente al que se le vendió un lote, con lo que se llevó sumado.
 *
 * Es la lista que se necesita en un recall: bloquear el lote lo saca del mostrador, pero avisar
 * exige saber a quién llamar.
 *
 * La lista NO es la venta completa del lote. La gran mayoría de las ventas del sistema son de
 * mostrador y van contra el cliente genérico, así que nunca se puede reconstruir a quién se le
 * vendió esa parte. Ese saldo se pide aparte con {@code resumenMostradorLote} y se muestra al
 * tope de la lista, para que la pantalla diga cuánto del lote quedó sin rastro en vez de aparentar
 * cobertura total.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClienteLoteDto {

    private Long clienteId;
    private String clienteNombre;
    private String clienteDocumento;
    /** Cuántas ventas distintas de este lote se le hicieron. */
    private Long ventas;
    /** Unidades del lote que se llevó, en positivo: el ledger las guarda negativas. */
    private Double cantidad;
    private LocalDateTime ultimaVenta;
}
