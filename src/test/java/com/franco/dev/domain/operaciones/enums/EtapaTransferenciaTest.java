package com.franco.dev.domain.operaciones.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre la regla de transiciones de etapa de una transferencia: solo se avanza.
 *
 * El caso que motiva esto son las transferencias 6284 y 6290, donde un cliente con una copia
 * vieja del header lo piso de vuelta a PRE_TRANSFERENCIA_ORIGEN despues de que la mercaderia
 * ya se habia recepcionado y el stock ya se habia movido.
 */
class EtapaTransferenciaTest {

    @Test
    @DisplayName("Se puede avanzar a la etapa siguiente")
    void avanzaALaSiguiente() {
        assertTrue(EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN
                .puedeAvanzarA(EtapaTransferencia.PREPARACION_MERCADERIA));
    }

    @Test
    @DisplayName("Reenviar la etapa actual se permite: no mueve nada")
    void reenviarLaMismaEtapa() {
        assertTrue(EtapaTransferencia.PREPARACION_MERCADERIA
                .puedeAvanzarA(EtapaTransferencia.PREPARACION_MERCADERIA));
    }

    @Test
    @DisplayName("No se puede retroceder de etapa")
    void noRetrocede() {
        assertFalse(EtapaTransferencia.RECEPCION_EN_VERIFICACION
                .puedeAvanzarA(EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN));
    }

    @Test
    @DisplayName("RECEPCION_CONCLUIDA es terminal: no acepta ninguna otra etapa")
    void recepcionConcluidaEsTerminal() {
        for (EtapaTransferencia destino : EtapaTransferencia.values()) {
            if (destino == EtapaTransferencia.RECEPCION_CONCLUIDA) continue;
            assertFalse(EtapaTransferencia.RECEPCION_CONCLUIDA.puedeAvanzarA(destino),
                    "no deberia poder pasar de RECEPCION_CONCLUIDA a " + destino);
        }
    }

    @Test
    @DisplayName("Se permite saltear etapas hacia adelante: el flujo real saltea TRANSPORTE_EN_DESTINO")
    void permiteSaltarHaciaAdelante() {
        assertTrue(EtapaTransferencia.TRANSPORTE_EN_CAMINO
                .puedeAvanzarA(EtapaTransferencia.RECEPCION_EN_VERIFICACION));
    }
}
