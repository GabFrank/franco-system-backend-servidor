package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Envío "en un paso" de un documento electrónico: lote de uno, vinculación y envío a SIFEN.
 * Lo usan las notas (remisión y crédito), que se emiten a pedido del usuario y no esperan al
 * scheduler. Las facturas siguen su camino de siempre.
 *
 * ⚠️ **Este servicio NO es transaccional, y eso es el diseño, no un olvido** (D8 del plan). Cada
 * uno de los tres pasos que invoca ya lleva su propio {@code @Transactional} en {@link SifenService},
 * y al llamarlos desde otro bean pasan por el proxy de Spring, así que **commitean por separado**:
 *
 *   T1 (antes de llegar acá) el número de la nota y el DE con su CDC — ya commiteados.
 *   T2 el lote y la vinculación.
 *   T3 el envío.
 *
 * Con una sola transacción envolvente, un timeout de lectura —SIFEN ya aceptó el lote pero la
 * respuesta no llegó— revertiría el número y el CDC, y el reintento tomaría el mismo número
 * comercial con otro CDC: dos documentos en SIFEN bajo el mismo número. Con los commits separados,
 * el peor caso es un DE en EN_LOTE con el lote en error, que el reenvío recupera consultando antes
 * el CDC.
 *
 * Por el mismo motivo **no se puede llamar a estos métodos desde adentro de SifenService**: la
 * auto-invocación no pasa por el proxy y las tres etapas caerían en la misma transacción.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sifen.enabled", havingValue = "true", matchIfMissing = false)
public class SifenEnvioSincronoService {

    private final SifenService sifenService;

    public SifenEnvioSincronoService(SifenService sifenService) {
        this.sifenService = sifenService;
    }

    /**
     * Crea un lote de un solo documento, lo vincula y lo envía. Devuelve el lote, cuyo estado dice
     * cómo terminó el envío (EN_PROCESO si SIFEN lo aceptó).
     */
    public LoteDE generarYEnviarSincrono(DocumentoElectronico de) throws SifenException {
        if (de == null || de.getId() == null) {
            throw new IllegalArgumentException("No hay documento electrónico para enviar");
        }

        LoteDE lote = sifenService.crearLote(de.getSucursalId());                 // T2
        sifenService.vincularDocumentosALote(lote, Collections.singletonList(de)); // T2
        log.info("📤 Enviando el documento {} en el lote {}", de.getId(), lote.getId());
        sifenService.enviarLote(lote);                                            // T3
        return lote;
    }
}
