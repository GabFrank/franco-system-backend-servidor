package com.franco.dev.scheduler;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDte;
import com.franco.dev.domain.financiero.DteEstado;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.repository.financiero.LoteDteRepository;
import com.franco.dev.service.financiero.DteNodeClient;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.DteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DteScheduler {

    private final DocumentoElectronicoRepository documentoElectronicoRepository;
    private final LoteDteRepository loteDteRepository;
    private final DteNodeClient nodeClient;
    private final FacturaLegalService facturaLegalService;
    private final DteService dteService;


    @Value("${dte.fecha-inicio:2025-08-27}")
    private String fechaInicio;


    @PostConstruct
    public void logConfiguration() {
        log.info("🔧 DTE Scheduler Config: fechaInicio={}", fechaInicio);
    }
    // Cada 10 minutos
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void procesarLotesPendientes() {
        log.info("[DTE] Buscando documentos 'GENERADO' para enviar a SIFEN");
        List<DocumentoElectronico> docs = documentoElectronicoRepository.findTop50ByEstadoSifenOrderByIdAsc(DteEstado.GENERADO.name());
        if (docs.isEmpty()) {
            log.info("[DTE] No hay documentos en estado GENERADO");
            return;
        }

        LoteDte nuevoLote = new LoteDte();
        nuevoLote.setFechaEnvio(LocalDateTime.now());
        nuevoLote.setEstadoSifen(DteEstado.ENVIADO.name());
        nuevoLote = loteDteRepository.save(nuevoLote);

        final LoteDte loteRef = nuevoLote;
        docs.forEach(d -> {
            d.setLote(loteRef);
            d.setEstadoSifen("ENVIADO");
        });
        documentoElectronicoRepository.saveAll(docs);

        try {
            // Enviar lote con documentos completos (XML + CDC)
            String idProtocolo = nodeClient.enviarLote(docs);
            nuevoLote.setIdProtocoloSifen(idProtocolo);
            nuevoLote.setEstadoSifen(DteEstado.RECIBIDO_POR_SIFEN.name());
            loteDteRepository.save(nuevoLote);
            log.info("[DTE] Lote {} enviado. Protocolo: {}", nuevoLote.getId(), idProtocolo);
        } catch (Exception e) {
            log.error("[DTE] Error enviando lote {}", nuevoLote.getId(), e);
            nuevoLote.setEstadoSifen(DteEstado.ERROR_ENVIO.name());
            loteDteRepository.save(nuevoLote);
            // Revertir estados
            docs.forEach(d -> d.setEstadoSifen(DteEstado.GENERADO.name()));
            documentoElectronicoRepository.saveAll(docs);
        }
    }

    // Cada 5 minutos
    @Scheduled(fixedRate = 150_000)
    @Transactional
    public void consultarResultadosDeLotes() {
        log.info("[DTE] Consultando resultados de lotes 'RECIBIDO_POR_SIFEN'");
        List<LoteDte> lotes = loteDteRepository.findByEstadoSifen(DteEstado.RECIBIDO_POR_SIFEN.name());
        if (lotes.isEmpty()) return;
        for (LoteDte lote : lotes) {
            try {
                log.info("[DTE] Consultando estado del lote {} con protocolo: {}", lote.getId(), lote.getIdProtocoloSifen());
                
                // Consultar estado del lote en SIFEN
                String respuesta = nodeClient.consultarLote(lote.getIdProtocoloSifen());
                lote.setRespuestaSifen(respuesta);
                
                // Procesar la respuesta para actualizar estados individuales
                List<DocumentoElectronico> docs = documentoElectronicoRepository.findByLoteId(lote.getId());
                boolean todosProcesados = procesarRespuestaSifen(docs, respuesta);
                
                if (todosProcesados) {
                    lote.setEstadoSifen(DteEstado.PROCESADO_OK.name());
                    log.info("[DTE] Lote {} procesado completamente. Respuesta: {}", lote.getId(), respuesta);
                } else {
                    lote.setEstadoSifen(DteEstado.PROCESADO_PARCIAL.name());
                    log.warn("[DTE] Lote {} procesado parcialmente. Respuesta: {}", lote.getId(), respuesta);
                }
                
                loteDteRepository.save(lote);
                
            } catch (Exception e) {
                log.error("[DTE] Error consultando lote {}", lote.getId(), e);
                lote.setEstadoSifen(DteEstado.ERROR_CONSULTA.name());
                loteDteRepository.save(lote);
            }
        }
    }

    /**
     * Procesa la respuesta de SIFEN para actualizar estados de documentos individuales
     * @param docs Lista de documentos del lote
     * @param respuesta Respuesta de SIFEN
     * @return true si todos los documentos fueron procesados
     */
    private boolean procesarRespuestaSifen(List<DocumentoElectronico> docs, String respuesta) {
        try {
            log.info("[DTE] Procesando respuesta de SIFEN para {} documentos", docs.size());
            
            // Análisis inteligente de la respuesta de SIFEN
            boolean respuestaValida = analizarRespuestaSifen(respuesta);
            
            if (!respuestaValida) {
                log.warn("[DTE] Respuesta de SIFEN no válida o incompleta, marcando como pendiente");
                for (DocumentoElectronico doc : docs) {
                    doc.setEstadoSifen(DteEstado.PENDIENTE_APROBACION.name());
                    doc.setMensajeSifen("Respuesta de SIFEN no válida - Reintentar consulta");
                }
                documentoElectronicoRepository.saveAll(docs);
                return false;
            }
            
            // Procesar cada documento según la respuesta
            for (DocumentoElectronico doc : docs) {
                String estado = determinarEstadoDocumento(respuesta);
                String mensaje = generarMensajeEstado(estado, respuesta);
                
                doc.setEstadoSifen(estado);
                doc.setMensajeSifen(mensaje);
                
                log.info("[DTE] Documento {} actualizado a estado: {} - {}", doc.getId(), estado, mensaje);
            }
            
            documentoElectronicoRepository.saveAll(docs);
            log.info("[DTE] Estados actualizados para {} documentos", docs.size());
            
            return true;
            
        } catch (Exception e) {
            log.error("[DTE] Error procesando respuesta de SIFEN", e);
            return false;
        }
    }
    
    /**
     * Analiza si la respuesta de SIFEN es válida y completa
     */
    private boolean analizarRespuestaSifen(String respuesta) {
        if (respuesta == null || respuesta.trim().isEmpty()) {
            return false;
        }
        
        // Verificar si es una respuesta HTML (error de sesión)
        if (respuesta.toLowerCase().contains("<html") || respuesta.toLowerCase().contains("<!doctype")) {
            log.warn("[DTE] Respuesta HTML detectada - posible error de sesión en SIFEN");
            return false;
        }
        
        // Verificar si es una respuesta XML válida
        if (respuesta.trim().startsWith("<?xml") || respuesta.trim().startsWith("<")) {
            return true;
        }
        
        // Verificar si contiene información de estado
        String respuestaLower = respuesta.toLowerCase();
        return respuestaLower.contains("estado") || 
               respuestaLower.contains("resultado") || 
               respuestaLower.contains("aprobado") || 
               respuestaLower.contains("rechazado") ||
               respuestaLower.contains("error");
    }
    
    /**
     * Determina el estado del documento basado en la respuesta de SIFEN
     */
    private String determinarEstadoDocumento(String respuesta) {
        String respuestaLower = respuesta.toLowerCase();
        
        // Detectar errores claros
        if (respuestaLower.contains("error") || 
            respuestaLower.contains("rechazado") || 
            respuestaLower.contains("denegado") ||
            respuestaLower.contains("invalido")) {
            return DteEstado.RECHAZADO.name();
        }
        
        // Detectar aprobaciones
        if (respuestaLower.contains("aprobado") || 
            respuestaLower.contains("aceptado") || 
            respuestaLower.contains("exitoso") ||
            respuestaLower.contains("ok")) {
            return DteEstado.APROBADO.name();
        }
        
        // Detectar estados pendientes
        if (respuestaLower.contains("pendiente") || 
            respuestaLower.contains("procesando") || 
            respuestaLower.contains("en revision")) {
            return DteEstado.PENDIENTE_APROBACION.name();
        }
        
        // Por defecto, marcar como pendiente para revisión manual
        return DteEstado.PENDIENTE_APROBACION.name();
    }
    
    /**
     * Genera un mensaje descriptivo del estado
     */
    private String generarMensajeEstado(String estado, String respuesta) {
        switch (estado) {
            case "APROBADO":
                return "Documento aprobado por SIFEN - " + extraerDetallesRespuesta(respuesta);
            case "RECHAZADO":
                return "Documento rechazado por SIFEN - " + extraerDetallesRespuesta(respuesta);
            case "PENDIENTE_APROBACION":
                return "Pendiente de aprobación - " + extraerDetallesRespuesta(respuesta);
            default:
                return "Estado: " + estado + " - " + extraerDetallesRespuesta(respuesta);
        }
    }
    
    /**
     * Extrae detalles relevantes de la respuesta de SIFEN
     */
    private String extraerDetallesRespuesta(String respuesta) {
        if (respuesta == null || respuesta.length() < 50) {
            return respuesta;
        }
        
        // Buscar líneas con información relevante
        String[] lineas = respuesta.split("\n");
        StringBuilder detalles = new StringBuilder();
        
        for (String linea : lineas) {
            String lineaLower = linea.toLowerCase().trim();
            if (lineaLower.contains("estado") || 
                lineaLower.contains("resultado") || 
                lineaLower.contains("mensaje") ||
                lineaLower.contains("motivo") ||
                lineaLower.contains("error")) {
                detalles.append(linea.trim()).append("; ");
            }
        }
        
        if (detalles.length() > 0) {
            return detalles.toString();
        }
        
        // Si no hay líneas específicas, devolver un resumen
        return respuesta.length() > 100 ? 
            respuesta.substring(0, 100) + "..." : 
            respuesta;
    }

    /**
     * Scheduler para detectar facturas legales sin DTE y procesarlas automáticamente
     * Se ejecuta cada 5 minutos para procesar facturas que llegan por replicación
     */
    @Scheduled(fixedRate = 300000) // 5 minutos = 300,000 ms
    public void procesarFacturasLegalesSinDte() {
        log.info("🚀 DTE: Procesando facturas legales sin DTE");
        procesarFacturasLegalesSinDteReal();
    }


    /**
     * Procesa facturas legales sin DTE en modo real
     */
    private void procesarFacturasLegalesSinDteReal() {
        try {
            log.info("🔍 DTE: Buscando facturas legales sin DTE...");
            
            // Buscar solo los IDs necesarios para evitar problemas de mapeo
            // Solo procesar facturas creadas a partir de la fecha configurada
            LocalDateTime fechaLimite = LocalDateTime.parse(fechaInicio + "T00:00:00");
            List<Object[]> facturasSinDteIds = facturaLegalService.findFacturasSinDteIds(fechaLimite);
            
            if (facturasSinDteIds.isEmpty()) {
                log.info("ℹ️ DTE: No hay facturas legales pendientes de DTE");
                return;
            }
            
            log.info("📋 DTE: Encontradas {} facturas legales sin DTE", facturasSinDteIds.size());
            
            int procesadas = 0;
            int errores = 0;
            
                            for (Object[] row : facturasSinDteIds) {
                try {
                    // Convertir BigInteger a Long para PostgreSQL
                    Long facturaId = convertToLong(row[0]);
                    Long ventaId = convertToLong(row[2]);
                    Long usuarioId = convertToLong(row[3]);
                    Long sucursalId = convertToLong(row[4]); // sucursal_id de factura_legal
                    
                    log.info("🚀 DTE: Procesando factura legal id={}, ventaId={}, usuarioId={}, sucursalId={}", 
                        facturaId, ventaId, usuarioId, sucursalId);
                    
                    // Generar DTE para esta factura usando su ID específico
                    // Usar el nuevo método que busca por ID de factura (más seguro)
                    dteService.generarDesdeFacturaLegalPorId(facturaId, sucursalId, usuarioId);
                    
                    procesadas++;
                    log.info("✅ DTE: Factura legal id={} procesada exitosamente", facturaId);
                    
                    // Pequeña pausa entre facturas para no sobrecargar
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    errores++;
                    log.error("❌ DTE: Error procesando factura legal", e);
                }
            }
            
            log.info("🏁 DTE: Procesamiento completado - {} procesadas, {} errores", procesadas, errores);
            
        } catch (Exception e) {
            log.error("❌ DTE: Error general en procesamiento de facturas legales sin DTE", e);
        }
    }

    /**
     * Convierte un Object a Long, manejando BigInteger de PostgreSQL
     * @param obj Objeto a convertir
     * @return Long convertido
     */
    private Long convertToLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        if (obj instanceof BigInteger) {
            return ((BigInteger) obj).longValue();
        }
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                log.warn("No se pudo convertir String a Long: {}", obj);
                return null;
            }
        }
        log.warn("Tipo no soportado para conversión a Long: {} ({})", obj, obj.getClass().getSimpleName());
        return null;
    }

    // Cada 2 minutos - Sincronizar estados de documentos con sus lotes (reducido para pruebas)
    @Scheduled(fixedRate = 120_000)
    @Transactional
    public void sincronizarEstadosDocumentos() {
        log.info("[DTE] Sincronizando estados de documentos con sus lotes");
        
        // Buscar documentos en estado ENVIADO que tengan lotes procesados
        List<DocumentoElectronico> docsParaSincronizar = documentoElectronicoRepository
            .findByEstadoSifenAndLoteIsNotNull(DteEstado.ENVIADO.name());
        
        if (docsParaSincronizar.isEmpty()) {
            log.info("[DTE] No hay documentos para sincronizar");
            return;
        }
        
        log.info("[DTE] Encontrados {} documentos para sincronizar", docsParaSincronizar.size());
        
        int sincronizados = 0;
        int errores = 0;
        
        for (DocumentoElectronico doc : docsParaSincronizar) {
            try {
                LoteDte lote = doc.getLote();
                if (lote != null) {
                    String estadoLote = lote.getEstadoSifen();
                    String mensajeLote = lote.getRespuestaSifen();
                    
                    // Actualizar estado del documento basándose en el estado del lote
                    if (DteEstado.PROCESADO_OK.name().equals(estadoLote)) {
                        // Lote procesado exitosamente - marcar como pendiente de aprobación
                        doc.setEstadoSifen(DteEstado.PENDIENTE_APROBACION.name());
                        doc.setMensajeSifen("Lote procesado por SIFEN - Pendiente de consulta de estado individual");
                        sincronizados++;
                        log.info("[DTE] Documento {} sincronizado: LOTE_PROCESSADO_OK → PENDIENTE_APROBACION", doc.getId());
                        
                    } else if (DteEstado.ERROR_CONSULTA.name().equals(estadoLote)) {
                        // Error en consulta - marcar como pendiente para reintentar
                        doc.setEstadoSifen(DteEstado.PENDIENTE_APROBACION.name());
                        doc.setMensajeSifen("Error en consulta de lote - Reintentar: " + (mensajeLote != null ? mensajeLote.substring(0, Math.min(100, mensajeLote.length())) : "Sin mensaje"));
                        sincronizados++;
                        log.info("[DTE] Documento {} sincronizado: ERROR_CONSULTA → PENDIENTE_APROBACION", doc.getId());
                        
                    } else if (DteEstado.PROCESADO_PARCIAL.name().equals(estadoLote)) {
                        // Lote procesado parcialmente
                        doc.setEstadoSifen(DteEstado.PENDIENTE_APROBACION.name());
                        doc.setMensajeSifen("Lote procesado parcialmente - Revisar estado individual");
                        sincronizados++;
                        log.info("[DTE] Documento {} sincronizado: PROCESADO_PARCIAL → PENDIENTE_APROBACION", doc.getId());
                        
                    } else if (DteEstado.RECIBIDO_POR_SIFEN.name().equals(estadoLote)) {
                        // Lote recibido por SIFEN - mantener como ENVIADO
                        log.debug("[DTE] Documento {} mantiene estado ENVIADO - Lote en RECIBIDO_POR_SIFEN", doc.getId());
                        
                    } else {
                        // Otros estados - marcar como pendiente
                        doc.setEstadoSifen(DteEstado.PENDIENTE_APROBACION.name());
                        doc.setMensajeSifen("Estado de lote no reconocido: " + estadoLote);
                        sincronizados++;
                        log.info("[DTE] Documento {} sincronizado: {} → PENDIENTE_APROBACION", doc.getId(), estadoLote);
                    }
                }
            } catch (Exception e) {
                errores++;
                log.error("[DTE] Error sincronizando documento {}", doc.getId(), e);
            }
        }
        
        // Guardar cambios
        if (sincronizados > 0) {
            documentoElectronicoRepository.saveAll(docsParaSincronizar);
            log.info("[DTE] Sincronización completada: {} documentos actualizados, {} errores", sincronizados, errores);
        }
    }

    // Cada 10 minutos - Consultar estados individuales de documentos pendientes
    // Optimizado para evitar rate limiting: menos frecuencia, menos documentos por ejecución
    @Scheduled(fixedRate = 600_000) // 10 minutos = 600,000 ms
    @Transactional
    public void consultarEstadosIndividuales() {
        log.info("[DTE] Consultando estados individuales de documentos 'PENDIENTE_APROBACION' (optimizado)");
        // Limitar a 10 documentos por ejecución para evitar rate limiting
        List<DocumentoElectronico> docsPendientes = documentoElectronicoRepository
            .findTop50ByEstadoSifenOrderByIdAsc(DteEstado.PENDIENTE_APROBACION.name())
            .stream()
            .limit(10) // Limitar a 10 documentos para rate limiting
            .collect(Collectors.toList());
        if (docsPendientes.isEmpty()) {
            log.info("[DTE] No hay documentos pendientes de aprobación");
            return;
        }
        log.info("[DTE] Encontrados {} documentos pendientes de aprobación", docsPendientes.size());
        int consultados = 0;
        int aprobados = 0;
        int rechazados = 0;
        int errores = 0;

        // Rate limiting: agregar delay entre consultas para evitar sobrecargar el microservicio
        int consultaIndex = 0;
        for (DocumentoElectronico doc : docsPendientes) {
            try {
                // Agregar delay progresivo entre consultas (500ms base + 200ms por consulta)
                if (consultaIndex > 0) {
                    long delay = 500 + (consultaIndex * 200);
                    log.debug("[DTE] Esperando {}ms antes de consultar documento {}", delay, doc.getId());
                    Thread.sleep(delay);
                }

                String estadoSifen = nodeClient.consultarEstadoIndividual(doc.getCdc()); // Corrected to nodeClient
                if ("APROBADO".equals(estadoSifen)) {
                    doc.setEstadoSifen(DteEstado.APROBADO.name());
                    doc.setMensajeSifen("Documento aprobado por SIFEN - " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    aprobados++;
                    log.info("[DTE] Documento {} APROBADO por SIFEN", doc.getId());
                } else if ("RECHAZADO".equals(estadoSifen)) {
                    doc.setEstadoSifen(DteEstado.RECHAZADO.name());
                    doc.setMensajeSifen("Documento rechazado por SIFEN - Motivo: Validación fallida");
                    rechazados++;
                    log.info("[DTE] Documento {} RECHAZADO por SIFEN", doc.getId());
                } else if ("PENDIENTE".equals(estadoSifen)) {
                    log.debug("[DTE] Documento {} mantiene estado PENDIENTE_APROBACION (SIFEN: en proceso)", doc.getId());
                } else if ("ERROR_CONSULTA".equals(estadoSifen)) {
                    log.warn("[DTE] Documento {} - Error en consulta a SIFEN, manteniendo PENDIENTE_APROBACION", doc.getId());
                } else {
                    log.warn("[DTE] Documento {} - Estado no reconocido: {}, manteniendo PENDIENTE_APROBACION", doc.getId(), estadoSifen);
                }
                consultados++;
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                // Manejo especial para 429: continuar con otros documentos pero con delay mayor
                errores++;
                log.warn("[DTE] Documento {} - Rate limiting alcanzado (429), esperando antes de continuar", doc.getId());
                try {
                    Thread.sleep(5000); // Esperar 5 segundos adicionales por rate limiting
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                errores++;
                log.error("[DTE] Error consultando estado individual del documento {}", doc.getId(), e);
                // Para otros errores, agregar un pequeño delay para evitar sobrecargar más
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                consultaIndex++; // Incrementar contador al final de cada iteración
            }
        }
        if (aprobados > 0 || rechazados > 0) {
            documentoElectronicoRepository.saveAll(docsPendientes);
            log.info("[DTE] Consulta individual completada: {} consultados, {} aprobados, {} rechazados, {} errores",
                consultados, aprobados, rechazados, errores);
        }
    }

    // NUEVO MÉTODO: Reintentar documentos en estado PROCESADO_PARCIAL
    // Cada 5 minutos - Reintentar documentos que SIFEN procesó parcialmente
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void reintentarDocumentosProcesadosParcialmente() {
        log.info("[DTE] Reintentando documentos en estado 'PROCESADO_PARCIAL'");
        
        // Buscar lotes en estado PROCESADO_PARCIAL
        List<LoteDte> lotesParciales = loteDteRepository.findByEstadoSifen(DteEstado.PROCESADO_PARCIAL.name());
        if (lotesParciales.isEmpty()) {
            log.debug("[DTE] No hay lotes en estado PROCESADO_PARCIAL");
            return;
        }
        
        log.info("[DTE] Encontrados {} lotes en estado PROCESADO_PARCIAL", lotesParciales.size());
        
        for (LoteDte lote : lotesParciales) {
            try {
                log.info("[DTE] Reintentando lote {} con protocolo: {}", lote.getId(), lote.getIdProtocoloSifen());
                
                // Consultar estado del lote nuevamente
                String respuesta = nodeClient.consultarLote(lote.getIdProtocoloSifen());
                lote.setRespuestaSifen(respuesta);
                
                // Procesar la respuesta para actualizar estados individuales
                List<DocumentoElectronico> docs = documentoElectronicoRepository.findByLoteId(lote.getId());
                boolean todosProcesados = procesarRespuestaSifen(docs, respuesta);
                
                if (todosProcesados) {
                    lote.setEstadoSifen(DteEstado.PROCESADO_OK.name());
                    log.info("[DTE] Lote {} procesado completamente en reintento. Respuesta: {}", lote.getId(), respuesta);
                } else {
                    // Si sigue parcial, verificar si es hora de marcar como error
                    LocalDateTime fechaEnvio = lote.getFechaEnvio();
                    if (fechaEnvio != null && fechaEnvio.plusHours(2).isBefore(LocalDateTime.now())) {
                        lote.setEstadoSifen(DteEstado.ERROR_CONSULTA.name());
                        lote.setRespuestaSifen("Error: Lote procesado parcialmente por más de 2 horas - " + respuesta);
                        log.warn("[DTE] Lote {} marcado como ERROR_CONSULTA después de 2 horas", lote.getId());
                    } else {
                        log.info("[DTE] Lote {} mantiene estado PROCESADO_PARCIAL, reintentando más tarde", lote.getId());
                    }
                }
                
                loteDteRepository.save(lote);
                
            } catch (Exception e) {
                log.error("[DTE] Error reintentando lote {}", lote.getId(), e);
                // No cambiar el estado en caso de error, reintentar en la próxima ejecución
            }
        }
    }

    // NUEVO MÉTODO: Corregir estados inconsistentes
    // Cada 10 minutos - Corregir documentos marcados incorrectamente como APROBADO
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void corregirEstadosInconsistentes() {
        log.info("[DTE] Verificando y corrigiendo estados inconsistentes");
        
        // Buscar documentos marcados como APROBADO pero cuyo lote está en PROCESADO_PARCIAL
        // Usar consulta nativa para evitar crear método específico en repositorio
        List<DocumentoElectronico> docsInconsistentes = documentoElectronicoRepository
            .findAll()
            .stream()
            .filter(doc -> DteEstado.APROBADO.name().equals(doc.getEstadoSifen()) &&
                          doc.getLote() != null && 
                          DteEstado.PROCESADO_PARCIAL.name().equals(doc.getLote().getEstadoSifen()))
            .collect(Collectors.toList());
        
        if (docsInconsistentes.isEmpty()) {
            log.debug("[DTE] No hay documentos con estados inconsistentes");
            return;
        }
        
        log.warn("[DTE] Encontrados {} documentos con estados inconsistentes (APROBADO pero lote PROCESADO_PARCIAL)", 
            docsInconsistentes.size());
        
        for (DocumentoElectronico doc : docsInconsistentes) {
            try {
                log.warn("[DTE] Corrigiendo documento {} - Estado inconsistente detectado", doc.getId());
                
                // Marcar como PENDIENTE_APROBACION para que se consulte individualmente
                doc.setEstadoSifen(DteEstado.PENDIENTE_APROBACION.name());
                doc.setMensajeSifen("Estado corregido - Requiere consulta individual a SIFEN - " + 
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                
                log.info("[DTE] Documento {} corregido a PENDIENTE_APROBACION", doc.getId());
                
            } catch (Exception e) {
                log.error("[DTE] Error corrigiendo documento {}", doc.getId(), e);
            }
        }
        
        if (!docsInconsistentes.isEmpty()) {
            documentoElectronicoRepository.saveAll(docsInconsistentes);
            log.info("[DTE] Estados inconsistentes corregidos para {} documentos", docsInconsistentes.size());
        }
    }
    

}


