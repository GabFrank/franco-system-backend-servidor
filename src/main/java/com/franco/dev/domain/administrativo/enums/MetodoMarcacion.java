package com.franco.dev.domain.administrativo.enums;

/**
 * Como se identifico a la persona al registrar la marcacion.
 *
 * Existe para poder evaluar el 1:N con datos y no con impresiones: cuando aparezca un caso
 * raro, sin esto no hay forma de distinguir un falso positivo de un olvido.
 *
 * Se guarda como texto y no como enum de Postgres a proposito: `administrativo.marcacion`
 * esta replicada en las dos direcciones, y un tipo nuevo obliga a crearlo tambien en cada
 * filial antes de que llegue la primera fila. Un VARCHAR no.
 */
public enum MetodoMarcacion {
    /** Sin verificacion facial: la persona marco y confirmo que iba igual. */
    MANUAL,
    /** Verificacion 1:1 contra la galeria del usuario en sesion, en su telefono. */
    FACIAL_1A1,
    /** Identificacion 1:N en el kiosco compartido. Es la que puede marcar por otro. */
    FACIAL_1AN_KIOSCO
}
