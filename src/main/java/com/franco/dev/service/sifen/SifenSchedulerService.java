package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Servicio programado para el procesamiento automático de lotes de Documentos Electrónicos.
 * 
 * Funciones principales:
 * 1. Busca DEs con estado PENDIENTE
 * 2. Los agrupa en lotes (max 50 DEs por lote según SIFEN)
 * 3. Envía los lotes a SIFEN
 * 4. Espera 5 segundos para que SIFEN procese
 * 5. Consulta los lotes enviados para actualizar estados
 * 
 * Configuración (application.properties):
 * - sifen.scheduler.enabled: Habilitar/deshabilitar scheduler (default: true)
 * - sifen.scheduler.fixed-delay: Intervalo entre ejecuciones en ms (default: 300000 = 5 min)
 * - sifen.lote.max-size: Máximo de DEs por lote (default: 50, máximo SIFEN)
 * - sifen.lote.max-retries: Máximo de reintentos por lote (default: 5)
 */
@Slf4j
@Service
@EnableScheduling
@ConditionalOnProperty(name = "sifen.enabled", havingValue = "true", matchIfMissing = false)
public class SifenSchedulerService {

    @Value("${sifen.scheduler.enabled:false}")
    private Boolean schedulerEnabled;
    
    @Value("${sifen.lote.max-size:50}")
    private Integer maxDocumentosPorLote;
    
    @Value("${sifen.lote.max-retries:5}")
    private Integer maxReintentos;

    /** Consulta de los lotes de notas (ver {@link #consultarLotesDeNotas}). Solo bodega la prende. */
    @Value("${sifen.notas.consulta.enabled:false}")
    private Boolean notasConsultaEnabled;

    /** SIFEN acepta la consulta de un lote solo dentro de las 48 h de recibido. */
    static final long HORAS_CONSULTA_POR_LOTE = 47;

    private final SifenService sifenService;
    private final DocumentoElectronicoService documentoElectronicoService;
    private final LoteDEService loteDEService;
    
    // Flag para evitar ejecuciones concurrentes
    private volatile boolean procesandoLotes = false;

    public SifenSchedulerService(
            SifenService sifenService,
            DocumentoElectronicoService documentoElectronicoService,
            LoteDEService loteDEService) {
        this.sifenService = sifenService;
        this.documentoElectronicoService = documentoElectronicoService;
        this.loteDEService = loteDEService;
    }

    /**
     * Tarea programada principal que ejecuta el flujo completo de procesamiento de lotes.
     * 
     * Configuración:
     * - fixedDelayString: Lee el intervalo desde application.properties
     * - initialDelay: Espera 30 segundos después del inicio de la aplicación
     */
    @Scheduled(fixedDelayString = "${sifen.scheduler.fixed-delay:300000}", initialDelay = 30000)
    public void procesarLotesAutomaticamente() {
        // Verificar si el scheduler está habilitado
        if (!schedulerEnabled) {
            log.debug("Scheduler de SIFEN deshabilitado");
            return;
        }
        
        // Evitar ejecuciones concurrentes
        if (procesandoLotes) {
            log.warn("⚠️ Ejecución anterior aún en proceso - omitiendo esta ejecución");
            return;
        }
        
        try {
            procesandoLotes = true;
            log.info("=================================================================");
            log.info("🤖 INICIANDO PROCESAMIENTO AUTOMÁTICO DE LOTES DE SIFEN");
            log.info("   Fecha/Hora: {}", LocalDateTime.now());
            log.info("=================================================================");
            
            // PASO 0: Recuperar lotes que quedaron sin enviarse (huerfanos)
            log.info("\n♻️  PASO 0: Recuperar lotes atrasados");
            procesarLotesAtrasados();

            // PASO 1: Crear y enviar lotes con DEs pendientes
            log.info("\n📦 PASO 1: Crear y enviar lotes con DEs pendientes");
            crearYEnviarLotes();
            
            // PASO 2: Esperar 5 segundos para que SIFEN procese los lotes
            log.info("\n⏳ PASO 2: Esperando 5 segundos para que SIFEN procese los lotes...");
            Thread.sleep(5000);
            
            // PASO 3: Consultar lotes en proceso
            log.info("\n🔍 PASO 3: Consultar lotes en proceso");
            consultarLotesPendientes();
            
            log.info("\n=================================================================");
            log.info("✅ PROCESAMIENTO AUTOMÁTICO COMPLETADO");
            log.info("=================================================================");
            
        } catch (InterruptedException e) {
            log.error("❌ Procesamiento interrumpido", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Error en procesamiento automático de lotes", e);
        } finally {
            procesandoLotes = false;
        }
    }

    /**
     * Trae la respuesta de SIFEN para las notas de crédito y de remisión.
     *
     * Las notas se envían en el momento, desde el central ({@link SifenEnvioSincronoService}), y el
     * lote queda EN_PROCESO. Sin este tick la respuesta solo la traía el scheduler general, que en
     * el central está apagado a propósito: su tabla de lotes recibe replicados los lotes de
     * facturas de las filiales, y prenderlo los reenviaría. Las notas quedaban EN_LOTE para siempre.
     *
     * Por eso toma solo lotes con DE de nota, y no reusa el bucle de {@link #consultarLotesPendientes}:
     * - **sin transacción envolvente**: cada {@code consultarLote} es la suya (otro bean, pasa por el
     *   proxy). Una envolvente se marca rollback-only con la excepción de un lote y pierde lo escrito
     *   en los demás. No anotar este método con {@code @Transactional};
     * - **sin contar intentos**: el general manda a ERROR_RED un lote con protocolo que SIFEN sigue
     *   procesando, y nadie lo vuelve a mirar. Acá un error se loguea y el lote se reintenta en la
     *   vuelta siguiente, hasta que SIFEN lo cierre o pase el corte de {@link #HORAS_CONSULTA_POR_LOTE};
     * - **por CDC pasado ese corte**, porque SIFEN ya no acepta la consulta del lote.
     */
    @Scheduled(fixedDelayString = "${sifen.notas.consulta.fixed-delay:120000}", initialDelay = 60000)
    public void consultarLotesDeNotas() {
        if (!Boolean.TRUE.equals(notasConsultaEnabled)) {
            return;
        }
        // El general ya consulta todos los EN_PROCESO: dos consultas en paralelo del mismo lote
        // pelean por el mismo registro.
        if (Boolean.TRUE.equals(schedulerEnabled)) {
            return;
        }
        if (procesandoLotes) {
            return;
        }
        try {
            procesandoLotes = true;
            List<LoteDE> lotes = loteDEService.findEnProcesoDeNotas();
            if (lotes.isEmpty()) {
                return;
            }
            log.info("🔍 Consultando {} lotes de notas en proceso", lotes.size());
            LocalDateTime corte = LocalDateTime.now().minusHours(HORAS_CONSULTA_POR_LOTE);
            for (LoteDE encontrado : lotes) {
                consultarLoteDeNota(encontrado, corte);
            }
        } catch (Exception e) {
            log.error("❌ Error al consultar los lotes de notas", e);
        } finally {
            procesandoLotes = false;
        }
    }

    private void consultarLoteDeNota(LoteDE encontrado, LocalDateTime corte) {
        try {
            // La mutation manual o un reenvío pudieron cerrarlo desde que se armó la lista.
            LoteDE lote = loteDEService
                .findByIdAndSucursalId(encontrado.getId(), encontrado.getSucursalId()).orElse(null);
            if (lote == null || lote.getEstado() != EstadoLoteDE.EN_PROCESO) {
                return;
            }
            if (lote.getCreadoEn() != null && lote.getCreadoEn().isBefore(corte)) {
                consultarLotePorCdc(lote);
            } else {
                sifenService.consultarLote(lote);
            }
        } catch (Exception e) {
            log.warn("⚠️ Lote de nota {} no se pudo consultar ({}): se reintenta en la próxima vuelta",
                encontrado.getId(), e.getMessage());
        }
    }

    /**
     * Consulta cada DE del lote por su CDC y deriva el estado del lote de lo que quedó, igual que
     * {@code SifenService.procesarRespuestaLoteConcluido}. Mientras algún DE siga EN_LOTE el lote no
     * cambia. Ojo: si SIFEN no conoce el CDC (0420), {@code consultarDE} deja el DE RECHAZADO.
     */
    private void consultarLotePorCdc(LoteDE lote) {
        List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos =
            documentoElectronicoService.findByLoteDe(lote);
        if (documentos.isEmpty()) {
            return;
        }
        for (com.franco.dev.domain.financiero.DocumentoElectronico de : documentos) {
            if (de.getEstado() == EstadoDE.EN_LOTE && de.getCdc() != null) {
                sifenService.consultarDE(de.getCdc());
            }
        }

        int aprobados = 0;
        int rechazados = 0;
        for (com.franco.dev.domain.financiero.DocumentoElectronico de : documentoElectronicoService.findByLoteDe(lote)) {
            if (de.getEstado() == EstadoDE.APROBADO || de.getEstado() == EstadoDE.CANCELADO) {
                aprobados++;
            } else if (de.getEstado() == EstadoDE.RECHAZADO) {
                rechazados++;
            } else {
                log.info("⏳ Lote de nota {}: el DE {} sigue {} tras consultar por CDC",
                    lote.getId(), de.getId(), de.getEstado());
                return;
            }
        }
        lote.setEstado(rechazados == 0 ? EstadoLoteDE.PROCESADO
            : aprobados == 0 ? EstadoLoteDE.RECHAZADO : EstadoLoteDE.PROCESADO_CON_ERRORES);
        lote.setFechaProcesado(LocalDateTime.now());
        loteDEService.save(lote);
        log.info("✅ Lote de nota {} cerrado por CDC: {}", lote.getId(), lote.getEstado());
    }

    /**
     * Reenvía los lotes que quedaron atrasados: creados pero nunca enviados con éxito
     * (PENDIENTE_ENVIO, ERROR_ENVIO, ERROR_RED). Portado del filial, que ya lo tenía.
     *
     * Sin esto un lote que falla al enviarse queda huérfano para siempre: sus DE quedan en EN_LOTE,
     * que no es lo que busca {@link #crearYEnviarLotes} (PENDIENTE) ni
     * {@link #consultarLotesPendientes} (lotes EN_PROCESO). Con las notas electrónicas, que se
     * envían en el momento y no por el scheduler, ese hueco es el modo de falla normal.
     *
     * Sin límite de reintentos, y saltea los lotes de menos de un minuto: pueden estar enviándose.
     */
    @Transactional
    public void procesarLotesAtrasados() {
        try {
            List<EstadoLoteDE> estadosParaProcesar = Arrays.asList(
                EstadoLoteDE.PENDIENTE_ENVIO,
                EstadoLoteDE.ERROR_ENVIO,
                EstadoLoteDE.ERROR_RED
            );
            List<LoteDE> lotesAtrasados = loteDEService.findByEstados(estadosParaProcesar);

            if (lotesAtrasados.isEmpty()) {
                return;
            }

            int lotesReenviados = 0;
            int lotesConError = 0;
            int lotesSinDocumentos = 0;

            for (LoteDE lote : lotesAtrasados) {
                try {
                    List<com.franco.dev.domain.financiero.DocumentoElectronico> documentos =
                        documentoElectronicoService.findByLoteDe(lote);

                    if (documentos.isEmpty()) {
                        log.warn("⚠️  Lote {} no tiene documentos asociados - marcando como ERROR_PERMANENTE",
                            lote.getId());
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        loteDEService.save(lote);
                        lotesSinDocumentos++;
                        continue;
                    }

                    // Un lote recién creado puede estar enviándose en este mismo momento
                    if (lote.getCreadoEn() != null &&
                        lote.getCreadoEn().isAfter(LocalDateTime.now().minusMinutes(1))) {
                        log.debug("   ⏳ Lote {} es muy reciente - se reintenta en la próxima vuelta",
                            lote.getId());
                        continue;
                    }

                    log.info("   🔄 Reintentando envío del lote {} (estado {}, intentos {})",
                        lote.getId(), lote.getEstado(), lote.getIntentos());
                    sifenService.enviarLote(lote);

                    LoteDE loteActualizado = loteDEService
                        .findByIdAndSucursalId(lote.getId(), lote.getSucursalId()).orElse(null);
                    if (loteActualizado != null
                            && loteActualizado.getEstado() == EstadoLoteDE.EN_PROCESO) {
                        lotesReenviados++;
                    } else {
                        lotesConError++;
                    }

                } catch (Exception e) {
                    log.error("❌ Error al reenviar lote {}: {}", lote.getId(), e.getMessage());
                    lote.setIntentos(lote.getIntentos() == null ? 1 : lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    // Se mantiene el estado recuperable: la próxima vuelta lo vuelve a tomar
                    loteDEService.save(lote);
                    lotesConError++;
                }
            }

            log.info("📊 Lotes atrasados: {} reenviados, {} con error, {} sin documentos (de {})",
                lotesReenviados, lotesConError, lotesSinDocumentos, lotesAtrasados.size());

        } catch (Exception e) {
            log.error("❌ Error al procesar lotes atrasados", e);
        }
    }

    /**
     * Crea lotes con todos los Documentos Electrónicos pendientes y los envía a SIFEN.
     * 
     * Lógica:
     * 1. Busca todos los DEs con estado PENDIENTE
     * 2. Los agrupa en lotes (máximo 50 por lote según SIFEN)
     * 3. Crea cada lote en BD
     * 4. Vincula los DEs al lote
     * 5. Envía el lote a SIFEN
     * 6. Actualiza el estado según la respuesta
     */
    @Transactional
    public void crearYEnviarLotes() {
        try {
            // 1. Buscar todos los DEs pendientes (sin lote asignado)
            List<com.franco.dev.domain.financiero.DocumentoElectronico> desPendientes = 
                documentoElectronicoService.findByEstado(EstadoDE.PENDIENTE);
            
            if (desPendientes.isEmpty()) {
                log.info("ℹ️  No hay DEs pendientes para procesar");
                return;
            }
            
            log.info("📋 Encontrados {} DEs pendientes para procesar", desPendientes.size());
            
            // 2. Agrupar DEs en lotes (máximo según configuración, default 50)
            List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = 
                dividirEnLotes(desPendientes, maxDocumentosPorLote);
            
            log.info("📦 Se crearán {} lotes", lotes.size());
            
            // 3. Procesar cada lote
            int lotesEnviados = 0;
            int lotesConError = 0;
            
            for (int i = 0; i < lotes.size(); i++) {
                List<com.franco.dev.domain.financiero.DocumentoElectronico> loteDEs = lotes.get(i);
                log.info("\n--- Procesando lote {} de {} ({} DEs) ---", i + 1, lotes.size(), loteDEs.size());
                
                try {
                    // 3.1. Crear lote en BD
                    LoteDE lote = sifenService.crearLote(loteDEs.get(0).getSucursalId());
                    log.info("✅ Lote creado con ID: {}", lote.getId());
                    
                    // 3.2. Vincular DEs al lote
                    sifenService.vincularDocumentosALote(lote, loteDEs);
                    log.info("✅ {} DEs vinculados al lote", loteDEs.size());
                    
                    // 3.3. Enviar lote a SIFEN
                    sifenService.enviarLote(lote);
                    
                    // Verificar resultado del envío
                    LoteDE loteActualizado = loteDEService.findByIdAndSucursalId(lote.getId(), lote.getSucursalId()).orElse(null);
                    if (loteActualizado != null) {
                        if (loteActualizado.getEstado() == EstadoLoteDE.EN_PROCESO) {
                            log.info("✅ Lote {} enviado exitosamente - Protocolo: {}", 
                                lote.getId(), loteActualizado.getProtocolo());
                            lotesEnviados++;
                        } else {
                            log.error("❌ Lote {} con error al enviar - Estado: {}", 
                                lote.getId(), loteActualizado.getEstado());
                            lotesConError++;
                        }
                    }
                    
                } catch (SifenException e) {
                    log.error("❌ Error de SIFEN al procesar lote {} de {}: {}", 
                        i + 1, lotes.size(), e.getMessage());
                    lotesConError++;
                } catch (Exception e) {
                    log.error("❌ Error inesperado al procesar lote {} de {}: {}", 
                        i + 1, lotes.size(), e.getMessage(), e);
                    lotesConError++;
                }
            }
            
            // 4. Resumen de procesamiento
            log.info("\n📊 RESUMEN DE ENVÍO DE LOTES:");
            log.info("   ✅ Lotes enviados exitosamente: {}", lotesEnviados);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   📋 Total procesados: {}", lotes.size());
            
        } catch (Exception e) {
            log.error("❌ Error al crear y enviar lotes", e);
        }
    }

    /**
     * Consulta todos los lotes con estado EN_PROCESO y actualiza sus estados.
     * 
     * Lógica:
     * 1. Busca lotes con estado EN_PROCESO
     * 2. Verifica que no excedan el máximo de reintentos
     * 3. Consulta el estado de cada lote en SIFEN
     * 4. Actualiza estados de lote y documentos según respuesta
     */
    @Transactional
    public void consultarLotesPendientes() {
        try {
            // 1. Buscar lotes en estado EN_PROCESO
            List<LoteDE> lotesEnProceso = loteDEService.findByEstado(EstadoLoteDE.EN_PROCESO);
            
            if (lotesEnProceso.isEmpty()) {
                log.info("ℹ️  No hay lotes en proceso para consultar");
                return;
            }
            
            log.info("📋 Encontrados {} lotes en proceso", lotesEnProceso.size());
            
            int lotesConsultados = 0;
            int lotesCompletados = 0;
            int lotesConError = 0;
            int lotesAunEnProceso = 0;
            
            // 2. Procesar cada lote
            for (LoteDE lote : lotesEnProceso) {
                log.info("\n--- Consultando lote {} (Protocolo: {}, Intento: {}/{}) ---", 
                    lote.getId(), lote.getProtocolo(), lote.getIntentos(), maxReintentos);
                
                try {
                    // Verificar límite de reintentos
                    if (lote.getIntentos() >= maxReintentos) {
                        log.warn("⚠️  Lote {} excedió el máximo de reintentos ({}) - marcando como ERROR_PERMANENTE", 
                            lote.getId(), maxReintentos);
                        lote.setEstado(EstadoLoteDE.ERROR_PERMANENTE);
                        loteDEService.save(lote);
                        lotesConError++;
                        continue;
                    }
                    
                    // Consultar estado del lote en SIFEN
                    sifenService.consultarLote(lote);
                    lotesConsultados++;
                    
                    // Verificar estado actualizado
                    LoteDE loteActualizado = loteDEService.findByIdAndSucursalId(lote.getId(), lote.getSucursalId()).orElse(null);
                    if (loteActualizado != null) {
                        switch (loteActualizado.getEstado()) {
                            case PROCESADO:
                            case PROCESADO_CON_ERRORES:
                                log.info("✅ Lote {} completado - Estado: {}", 
                                    lote.getId(), loteActualizado.getEstado());
                                lotesCompletados++;
                                break;
                            case EN_PROCESO:
                                log.info("⏳ Lote {} aún en procesamiento", lote.getId());
                                lotesAunEnProceso++;
                                break;
                            case ERROR_PERMANENTE:
                            case RECHAZADO:
                                log.error("❌ Lote {} con error - Estado: {}", 
                                    lote.getId(), loteActualizado.getEstado());
                                lotesConError++;
                                break;
                            default:
                                log.warn("⚠️  Lote {} con estado inesperado: {}", 
                                    lote.getId(), loteActualizado.getEstado());
                                break;
                        }
                    }
                    
                } catch (SifenException e) {
                    log.error("❌ Error de SIFEN al consultar lote {}: {}", 
                        lote.getId(), e.getMessage());
                    
                    // Incrementar contador de intentos
                    lote.setIntentos(lote.getIntentos() + 1);
                    lote.setFechaUltimoIntento(LocalDateTime.now());
                    
                    // Si excede reintentos, marcar como error
                    if (lote.getIntentos() >= maxReintentos) {
                        lote.setEstado(EstadoLoteDE.ERROR_RED);
                        log.error("❌ Lote {} marcado como ERROR_RED después de {} intentos", 
                            lote.getId(), lote.getIntentos());
                    }
                    
                    loteDEService.save(lote);
                    lotesConError++;
                    
                } catch (Exception e) {
                    log.error("❌ Error inesperado al consultar lote {}: {}", 
                        lote.getId(), e.getMessage(), e);
                    lotesConError++;
                }
            }
            
            // 3. Resumen de consultas
            log.info("\n📊 RESUMEN DE CONSULTA DE LOTES:");
            log.info("   ✅ Lotes completados: {}", lotesCompletados);
            log.info("   ⏳ Lotes aún en proceso: {}", lotesAunEnProceso);
            log.info("   ❌ Lotes con error: {}", lotesConError);
            log.info("   🔍 Total consultados: {}", lotesConsultados);
            
        } catch (Exception e) {
            log.error("❌ Error al consultar lotes pendientes", e);
        }
    }

    /**
     * Divide una lista de DEs en lotes más pequeños.
     * 
     * @param des Lista de documentos electrónicos
     * @param maxSize Tamaño máximo por lote
     * @return Lista de lotes (cada lote es una lista de DEs)
     */
    private List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> dividirEnLotes(
            List<com.franco.dev.domain.financiero.DocumentoElectronico> des, int maxSize) {
        
        List<List<com.franco.dev.domain.financiero.DocumentoElectronico>> lotes = new ArrayList<>();
        
        for (int i = 0; i < des.size(); i += maxSize) {
            int end = Math.min(i + maxSize, des.size());
            lotes.add(des.subList(i, end));
        }
        
        return lotes;
    }
    
    /**
     * Método público para forzar una ejecución manual del scheduler.
     * Útil para pruebas o ejecuciones on-demand desde GraphQL.
     */
    public void ejecutarManualmente() {
        log.info("🔧 Ejecución manual del scheduler solicitada");
        procesarLotesAutomaticamente();
    }
}

