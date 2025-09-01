package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoDte;
import com.franco.dev.domain.financiero.DteEstado;
import com.franco.dev.domain.financiero.EventoTipo;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.repository.financiero.DocumentoElectronicoRepository;
import com.franco.dev.repository.financiero.EventoDteRepository;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;

import java.util.ArrayList;
import java.util.List;
import static com.franco.dev.utilitarios.CalcularVerificadorRuc.getDigitoVerificadorString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@AllArgsConstructor
public class DteService {

    private static final Logger log = LoggerFactory.getLogger(DteService.class);

    private final DocumentoElectronicoRepository documentoElectronicoRepository;
    private final EventoDteRepository eventoDteRepository;
    private final FacturaLegalRepository facturaLegalRepository;
    private final DteNodeClient dteNodeClient;
    private final UsuarioService usuarioService;

    public UsuarioService getUsuarioService() {
        return usuarioService;
    }

    @Transactional
    public DocumentoElectronico iniciarGeneracionDte(Long ventaId, Long sucursalId, Long usuarioId) {
        log.info("🔍 DTE: iniciarGeneracionDte - ventaId={}, sucursalId={}, usuarioId={}", ventaId, sucursalId, usuarioId);
        
        // IMPORTANTE: Si ventaId es null, necesitamos buscar la factura de otra manera
        // Para evitar el problema de múltiples resultados, usamos una estrategia diferente
        FacturaLegal facturaLegal;
        
        if (ventaId != null) {
            // Si tenemos ventaId, buscar por ventaId y sucursalId
            facturaLegal = facturaLegalRepository.findByVentaIdAndSucursalId(ventaId, sucursalId);
        } else {
            // Si ventaId es null, buscar por sucursalId y venta IS NULL (pero limitar a 1 resultado)
            log.warn("⚠️ DTE: ventaId es null, buscando factura sin venta asociada...");
            // Por ahora, retornamos null para evitar el problema
            // TODO: Implementar búsqueda más específica si es necesario
            log.error("❌ DTE: No se puede procesar factura sin ventaId en iniciarGeneracionDte");
            return null;
        }
        
        if (facturaLegal == null) {
            log.error("❌ DTE: FacturaLegal no encontrada en iniciarGeneracionDte");
            return null;
        }
        log.info("✅ DTE: FacturaLegal encontrada en iniciarGeneracionDte, id={}", facturaLegal.getId());

        // Validaciones previas, bloquea si hay errores
        log.info("🔍 DTE: Ejecutando validaciones previas...");
        List<String> errores = validarFacturaLegalParaDte(facturaLegal);
        if (!errores.isEmpty()) {
            log.error("❌ DTE: Validaciones fallaron: {}", errores);
            throw new GraphQLException("Validación DTE: " + String.join("; ", errores));
        }
        log.info("✅ DTE: Validaciones previas exitosas");

        DocumentoElectronico existente = documentoElectronicoRepository.findAll()
                .stream()
                .filter(d -> d.getFacturaLegal() != null
                        && d.getFacturaLegal().getId().equals(facturaLegal.getId())
                        && d.getFacturaLegal().getSucursalId().equals(facturaLegal.getSucursalId()))
                .findFirst()
                .orElse(null);
        if (existente != null) {
            log.info("ℹ️ DTE: Ya existe DTE, retornando existente id={}", existente.getId());
            return existente;
        }

        log.info("🚀 DTE: Creando nuevo DocumentoElectronico...");
        DocumentoElectronico dte = new DocumentoElectronico();
        dte.setEstadoSifen(DteEstado.PENDIENTE.name());
        dte.setFacturaLegal(facturaLegal);
        if (usuarioId != null) dte.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        dte = documentoElectronicoRepository.save(dte);
        log.info("✅ DTE: DocumentoElectronico creado y guardado, id={}", dte.getId());
        
        log.info("🚀 DTE: Llamando a generarYFirmarXmlConNode...");
        generarYFirmarXmlConNode(dte.getId(), usuarioId);
        log.info("✅ DTE: generarYFirmarXmlConNode completado");
        
        return dte;
    }

    public Page<DocumentoElectronico> findAll(int page, int size) {
        return documentoElectronicoRepository.findAll(PageRequest.of(page, size));
    }

    public Page<DocumentoElectronico> findFiltered(String estado, String fechaDesde, String fechaHasta, int page, int size, String cdc, Long sucursalId) {
        PageRequest pr = PageRequest.of(page, size);
        boolean hasEstado = estado != null;
        boolean hasDesde = fechaDesde != null;
        boolean hasHasta = fechaHasta != null;
        boolean hasCdc = cdc != null    ;
        boolean hasSuc = sucursalId != null;
        
        if (hasCdc) return documentoElectronicoRepository.findByCdcContainingIgnoreCase(cdc, pr);
        
        if (hasSuc && !hasDesde && !hasHasta && !hasEstado) {
            return documentoElectronicoRepository.findByFacturaLegal_SucursalId(sucursalId, pr);
        }
        if (hasSuc && !hasDesde && !hasHasta && hasEstado) {
            return documentoElectronicoRepository.findByFacturaLegal_SucursalIdAndEstadoSifen(sucursalId, estado, pr);
        }
        if (hasSuc && (hasDesde || hasHasta) && !hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByFacturaLegal_SucursalIdAndCreadoEnBetween(sucursalId, desde, hasta, pr);
        }
        if (hasSuc && (hasDesde || hasHasta) && hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByFacturaLegal_SucursalIdAndEstadoSifenAndCreadoEnBetween(sucursalId, estado, desde, hasta, pr);
        }
        
        if (!hasDesde && !hasHasta && hasEstado) {
            return documentoElectronicoRepository.findByEstadoSifen(estado, pr);
        }
        
        if ((hasDesde || hasHasta) && !hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByCreadoEnBetween(desde, hasta, pr);
        }
        
        if ((hasDesde || hasHasta) && hasEstado) {
            LocalDateTime desde = stringToDate(fechaDesde);
            LocalDateTime hasta = stringToDate(fechaHasta);
            return documentoElectronicoRepository.findByEstadoSifenAndCreadoEnBetween(estado, desde, hasta, pr);
        }
        
        return documentoElectronicoRepository.findAll(pr);
    }

    public DocumentoElectronico findById(Long id) {
        return documentoElectronicoRepository.findById(id).orElse(null);
    }

    @Transactional
    public DocumentoElectronico generarDesdeFacturaLegalSiNoExiste(Long ventaId, Long sucursalId, Long usuarioId) {
        log.info("🔍 DTE: Iniciando generación para ventaId={}, sucursalId={}, usuarioId={}", ventaId, sucursalId, usuarioId);
        
        FacturaLegal facturaLegal = facturaLegalRepository.findByVentaIdAndSucursalId(ventaId, sucursalId);
        if (facturaLegal == null) {
            log.error("❌ DTE: FacturaLegal no encontrada para ventaId={}, sucursalId={}", ventaId, sucursalId);
            return null;
        }
        log.info("✅ DTE: FacturaLegal encontrada id={}, numero={}", facturaLegal.getId(), facturaLegal.getNumeroFactura());
        
        DocumentoElectronico ya = documentoElectronicoRepository.findFirstByFacturaLegal_IdAndFacturaLegal_SucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());
        if (ya != null) {
            log.info("ℹ️ DTE: Ya existe DTE para esta factura, id={}", ya.getId());
            return ya;
        }
        
        log.info("🚀 DTE: Iniciando generación de DTE...");
        try {
            DocumentoElectronico resultado = iniciarGeneracionDte(ventaId, sucursalId, usuarioId);
            log.info("✅ DTE: Generación completada, resultado={}", resultado != null ? resultado.getId() : "null");
            return resultado;
        } catch (Exception e) {
            log.error("❌ DTE: Error durante la generación", e);
            throw e;
        }
    }

    /**
     * Genera DTE desde una factura legal específica por su ID
     * Este método es más seguro para el scheduler que busca facturas por ID
     */
    @Transactional
    public DocumentoElectronico generarDesdeFacturaLegalPorId(Long facturaId, Long sucursalId, Long usuarioId) {
        log.info("🔍 DTE: Iniciando generación por ID para facturaId={}, sucursalId={}, usuarioId={}", facturaId, sucursalId, usuarioId);
        
        // Buscar la factura por ID y sucursal (más seguro)
        FacturaLegal facturaLegal = facturaLegalRepository.findByIdAndSucursalId(facturaId, sucursalId);
        if (facturaLegal == null) {
            log.error("❌ DTE: FacturaLegal no encontrada para facturaId={}, sucursalId={}", facturaId, sucursalId);
            return null;
        }
        log.info("✅ DTE: FacturaLegal encontrada id={}, numero={}, ventaId={}", 
            facturaLegal.getId(), facturaLegal.getNumeroFactura(), facturaLegal.getVenta() != null ? facturaLegal.getVenta().getId() : "null");
        
        // Verificar si ya existe DTE
        DocumentoElectronico ya = documentoElectronicoRepository.findFirstByFacturaLegal_IdAndFacturaLegal_SucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());
        if (ya != null) {
            log.info("ℹ️ DTE: Ya existe DTE para esta factura, id={}", ya.getId());
            return ya;
        }
        
        log.info("🚀 DTE: Iniciando generación de DTE...");
        try {
            // Verificar si la factura tiene venta asociada
            if (facturaLegal.getVenta() != null) {
                // Si tiene venta, usar el método normal
                Long ventaId = facturaLegal.getVenta().getId();
                DocumentoElectronico resultado = iniciarGeneracionDte(ventaId, sucursalId, usuarioId);
                log.info("✅ DTE: Generación completada (con venta), resultado={}", resultado != null ? resultado.getId() : "null");
                return resultado;
            } else {
                // Si NO tiene venta, crear el DTE directamente
                log.info("ℹ️ DTE: Factura sin venta asociada, creando DTE directamente...");
                
                // Verificar si ya existe DTE
                DocumentoElectronico existente = documentoElectronicoRepository.findFirstByFacturaLegal_IdAndFacturaLegal_SucursalId(facturaLegal.getId(), facturaLegal.getSucursalId());
                if (existente != null) {
                    log.info("ℹ️ DTE: Ya existe DTE para esta factura sin venta, id={}", existente.getId());
                    return existente;
                }
                
                // Crear DTE directamente
                DocumentoElectronico dte = new DocumentoElectronico();
                dte.setEstadoSifen(DteEstado.PENDIENTE.name());
                dte.setFacturaLegal(facturaLegal);
                if (usuarioId != null) dte.setUsuario(usuarioService.findById(usuarioId).orElse(null));
                dte = documentoElectronicoRepository.save(dte);
                log.info("✅ DTE: DocumentoElectronico creado directamente, id={}", dte.getId());
                
                // Generar XML
                generarYFirmarXmlConNode(dte.getId(), usuarioId);
                log.info("✅ DTE: XML generado para factura sin venta");
                
                return dte;
            }
        } catch (Exception e) {
            log.error("❌ DTE: Error durante la generación", e);
            throw e;
        }
    }

    @Transactional
    public EventoDte registrarEvento(Long dteId, Integer tipoEvento, Long usuarioId, String motivo, String observacion) {
        DocumentoElectronico dte = documentoElectronicoRepository.findById(dteId).orElse(null);
        if (dte == null) return null;
        EventoDte evento = new EventoDte();
        evento.setDocumentoElectronico(dte);
        evento.setTipoEvento(tipoEvento);
        evento.setFechaEvento(java.time.LocalDateTime.now());
        if (usuarioId != null) {
            evento.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        }
        if (motivo != null) evento.setMotivo(motivo);
        if (observacion != null) evento.setObservacion(observacion);
        evento.setCreadoEn(java.time.LocalDateTime.now());
        // Llamada al Node (mock o real) para registrar el evento
        try {
            DteNodeClient.RegistrarEventoResponse resp = dteNodeClient.registrarEvento(dte.getCdc(), tipoEvento, motivo, observacion);
            if (resp != null) {
                evento.setCdcEvento(resp.getCdcEvento());
                evento.setMensajeRespuestaSifen(resp.getMensaje());
            }
        } catch (Exception ignored) {}
        evento = eventoDteRepository.save(evento);
        // Actualización de estado del DTE según tipo de evento (p. ej., 1 = Cancelación)
        EventoTipo et = EventoTipo.fromCode(tipoEvento);
        if (et == EventoTipo.CANCELACION) {
            dte.setEstadoSifen(DteEstado.CANCELADO.name());
            documentoElectronicoRepository.save(dte);
        }
        return evento;
    }

    public java.util.List<EventoDte> listarEventosPorDte(Long dteId) {
        return eventoDteRepository.findByDocumentoElectronicoIdOrderByIdAsc(dteId);
    }

    @Transactional
    public void generarYFirmarXmlConNode(Long dteId, Long usuarioId) {
        log.info("🔍 DTE: generarYFirmarXmlConNode - dteId={}, usuarioId={}", dteId, usuarioId);
        
        DocumentoElectronico dte = documentoElectronicoRepository.findById(dteId).orElse(null);
        if (dte == null) {
            log.error("❌ DTE: DocumentoElectronico no encontrado, dteId={}", dteId);
            return;
        }
        log.info("✅ DTE: DocumentoElectronico encontrado, estado={}", dte.getEstadoSifen());
        
        if (usuarioId != null) dte.setUsuario(usuarioService.findById(usuarioId).orElse(null));
        Long facturaId = dte.getFacturaLegal() != null ? dte.getFacturaLegal().getId() : null;
        Long sucursalId = dte.getFacturaLegal() != null ? dte.getFacturaLegal().getSucursalId() : null;
        log.info("🔍 DTE: facturaId={}, sucursalId={}", facturaId, sucursalId);

        // Validaciones previas a firma/generación
        if (dte.getFacturaLegal() != null) {
            log.info("🔍 DTE: Ejecutando validaciones previas a firma...");
            List<String> errores = validarFacturaLegalParaDte(dte.getFacturaLegal());
            if (!errores.isEmpty()) {
                log.error("❌ DTE: Validaciones previas a firma fallaron: {}", errores);
                dte.setMensajeSifen("Validación DTE: " + String.join("; ", errores));
                documentoElectronicoRepository.save(dte);
                return;
            }
            log.info("✅ DTE: Validaciones previas a firma exitosas");
        }
        
        log.info("🚀 DTE: Llamando al microservicio para generar documento...");
        try {
            log.info("🔍 DTE: URL del microservicio: {} + {}", dteNodeClient.getBaseUrl(), dteNodeClient.getGenerarEndpoint());
            log.info("🔍 DTE: Enviando facturaId={}, sucursalId={}", facturaId, sucursalId);
            
            DteNodeClient.GenerarDocumentoResponse res = dteNodeClient.generarDocumentoDesdeFactura(facturaId, sucursalId);
            
            if (res != null) {
                log.info("✅ DTE: Respuesta del microservicio recibida");
                log.info("🔍 DTE: CDC recibido: '{}'", res.getCdc());
                log.info("🔍 DTE: XML recibido: {} caracteres", res.getXmlFirmado() != null ? res.getXmlFirmado().length() : "null");
                log.info("🔍 DTE: QR recibido: '{}'", res.getUrlQr());
                
                dte.setXmlFirmado(res.getXmlFirmado());
                dte.setCdc(res.getCdc());
                dte.setUrlQr(res.getUrlQr());
                
                // Cambiar estado a GENERADO para que el scheduler pueda procesarlo
                dte.setEstadoSifen(DteEstado.GENERADO.name());
                
                // Guardar el DTE primero
                dte = documentoElectronicoRepository.save(dte);
                log.info("✅ DTE: DTE actualizado y guardado exitosamente");
                
                // Actualizar la FacturaLegal para indicar que tiene DTE generado
                if (dte.getFacturaLegal() != null) {
                    FacturaLegal facturaLegal = dte.getFacturaLegal();
                    
                    // Establecer la relación bidireccional
                    facturaLegal.setDocumentoElectronico(dte);
                    
                    // Marcar que la factura tiene DTE generado (opcional: agregar campo en FacturaLegal)
                    // facturaLegal.setTieneDte(true); // Si agregas este campo
                    
                    // Guardar la factura actualizada
                    facturaLegalRepository.save(facturaLegal);
                    log.info("✅ DTE: FacturaLegal actualizada con relación al DTE, facturaId={}", facturaLegal.getId());
                }
                
            } else {
                log.error("❌ DTE: Respuesta del microservicio es null");
            }
        } catch (Exception e) {
            log.error("❌ DTE: Error en comunicación con microservicio", e);
            dte.setMensajeSifen("Error: " + e.getMessage());
            documentoElectronicoRepository.save(dte);
        }
    }

    // Validaciones mínimas previas según Manual (se ampliarán en real mode)
    private List<String> validarFacturaLegalParaDte(FacturaLegal f) {
        List<String> errores = new ArrayList<>();
        if (f == null) {
            errores.add("Factura no encontrada");
            return errores;
        }
        // Timbrado presente y vigente por fechas
        if (f.getTimbradoDetalle() == null || f.getTimbradoDetalle().getTimbrado() == null) {
            errores.add("Timbrado no asignado");
        } else {
            if (f.getTimbradoDetalle().getTimbrado().getFechaInicio() != null && java.time.LocalDate.now().isBefore(f.getTimbradoDetalle().getTimbrado().getFechaInicio().toLocalDate())) {
                errores.add("Timbrado aún no vigente");
            }
            if (f.getTimbradoDetalle().getTimbrado().getFechaFin() != null && java.time.LocalDate.now().isAfter(f.getTimbradoDetalle().getTimbrado().getFechaFin().toLocalDate())) {
                errores.add("Timbrado vencido");
            }
        }
        // Receptor
        if (f.getNombre() == null || f.getNombre().trim().isEmpty()) errores.add("Nombre del receptor requerido");
        if (f.getRuc() == null || f.getRuc().trim().isEmpty()) {
            errores.add("RUC/CI del receptor requerido");
        } else if (f.getRuc().contains("-")) {
            try {
                String[] parts = f.getRuc().split("-");
                String base = parts[0];
                String dv = parts[1];
                String dvCalc = getDigitoVerificadorString(base);
                if (!dv.equals(dvCalc)) errores.add("RUC con dígito verificador inválido");
            } catch (Exception ignored) {}
        }
        // Totales
        if (f.getTotalFinal() == null || f.getTotalFinal() <= 0) errores.add("Total final inválido");
        return errores;
    }
}


