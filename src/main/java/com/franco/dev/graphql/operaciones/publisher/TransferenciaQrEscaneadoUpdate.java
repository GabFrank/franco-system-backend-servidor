package com.franco.dev.graphql.operaciones.publisher;

import lombok.Data;

/**
 * Aviso de que alguien escaneó el QR de una transferencia desde el móvil.
 *
 * No representa ningún cambio de estado del negocio: la transferencia no se
 * modifica al escanearla. Es solo la señal que le permite al desktop cerrar
 * el diálogo del QR cuando ya cumplió su función, en vez de dejarlo abierto
 * hasta que alguien lo cierre a mano.
 */
@Data
public class TransferenciaQrEscaneadoUpdate {

    private Long transferenciaId;

    private Long sucursalId;
}
