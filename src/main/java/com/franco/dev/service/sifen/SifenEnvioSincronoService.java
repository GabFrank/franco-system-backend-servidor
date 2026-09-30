package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.LoteDEService;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final LoteDEService loteDEService;

    public SifenEnvioSincronoService(SifenService sifenService, LoteDEService loteDEService) {
        this.sifenService = sifenService;
        this.loteDEService = loteDEService;
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

    /**
     * Antes de reenviar o anular una nota cuyo DE está EN_LOTE, le pregunta a SIFEN cómo quedó.
     * Sin esto, el reenvío armaba un segundo lote con el mismo CDC mientras el primero seguía vivo,
     * y la anulación daba de baja solo en el sistema una nota que SIFEN tenía aprobada.
     *
     * Con el lote vivo se consulta **por lote**: mientras SIFEN lo procesa puede contestar «no
     * existe» (0420) por el CDC, y {@code consultarDE} marcaría RECHAZADO una nota que después sale
     * aprobada. Por CDC solo con el lote muerto o pasado el plazo de consulta de lotes. Un error de
     * la consulta se loguea: quien llama decide con el estado que haya en la base.
     */
    public void refrescarSiEnLote(DocumentoElectronico de) {
        if (de == null || de.getEstado() != EstadoDE.EN_LOTE) {
            return;
        }
        LoteDE lote = lote(de);
        try {
            if (consultablePorLote(lote)) {
                sifenService.consultarLote(lote);
            } else if (de.getCdc() != null) {
                sifenService.consultarDE(de.getCdc());
            }
        } catch (Exception e) {
            log.warn("⚠️ No se pudo consultar a SIFEN el documento {}: {}", de.getId(), e.getMessage());
        }
    }

    /** El DE sigue EN_LOTE y su lote está EN_PROCESO: SIFEN todavía no respondió. */
    public boolean sigueEnProceso(DocumentoElectronico de) {
        if (de == null || de.getEstado() != EstadoDE.EN_LOTE) {
            return false;
        }
        LoteDE lote = lote(de);
        return lote != null && lote.getEstado() == EstadoLoteDE.EN_PROCESO;
    }

    private LoteDE lote(DocumentoElectronico de) {
        if (de.getLoteDeId() == null) {
            return null;
        }
        return loteDEService.findByIdAndSucursalId(de.getLoteDeId(), de.getSucursalId()).orElse(null);
    }

    private static boolean consultablePorLote(LoteDE lote) {
        return lote != null
                && lote.getEstado() == EstadoLoteDE.EN_PROCESO
                && lote.getProtocolo() != null
                && (lote.getCreadoEn() == null || lote.getCreadoEn().isAfter(
                        LocalDateTime.now().minusHours(SifenSchedulerService.HORAS_CONSULTA_POR_LOTE)));
    }
}
