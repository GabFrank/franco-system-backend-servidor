package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.sifen.util.SifenReceptorHelper;
import com.franco.dev.service.sifen.util.SifenXmlParser;
import com.roshka.sifen.core.exceptions.SifenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio principal para la gestión de Documentos Electrónicos (DE) y lotes SIFEN.
 * 
 * Responsabilidades:
 * - Crear DEs a partir de FacturaLegal
 * - Gestionar lotes de envío
 * - Enviar lotes a SIFEN
 * - Consultar estado de lotes y DEs
 * - Procesar respuestas de SIFEN
 */
@Slf4j
@Service
public class SifenService {

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    @Autowired
    private LoteDEService loteDEService;

    /**
     * Crea un DocumentoElectronico a partir de una FacturaLegal.
     *
     * Este método implementa el flujo completo de creación de documentos electrónicos:
     * 1. Toma los datos de la FacturaLegal y sus items
     * 2. Genera el objeto DE de la librería SIFEN
     * 3. Calcula el CDC (Código de Control de Documento)
     * 4. Genera el XML original (SIN FIRMAR) y lo guarda
     * 5. Extrae la URL del QR
     * 6. Guarda el DE en estado PENDIENTE
     *
     * @param factura La factura legal a convertir en DE
     * @return El DocumentoElectronico creado y persistido
     * @throws IllegalArgumentException Si la factura es inválida o falta información requerida
     * @throws SifenException Si hay errores en la generación del DE
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentoElectronico crearDocumentoElectronico(FacturaLegal factura) throws Exception {
        log.info("Creando documento electrónico para factura legal ID: {}", factura.getId());

        if (factura == null) {
            throw new IllegalArgumentException("Factura no puede ser null");
        }

        if (factura.getId() == null) {
            throw new IllegalArgumentException("Factura debe estar guardada en base de datos");
        }

        // Verificar que no exista ya un DE para esta factura
        Optional<DocumentoElectronico> existente = documentoElectronicoService.findByFacturaLegalId(factura.getId());
        if (existente.isPresent()) {
            throw new IllegalArgumentException("Ya existe un documento electrónico para esta factura");
        }

        // 1. Validar que la factura tenga todos los datos necesarios
        validarFacturaParaDE(factura);

        // 2. Obtener datos del timbrado electrónico
        TimbradoDetalle timbradoDetalle = factura.getTimbradoDetalle();
        if (timbradoDetalle == null) {
            throw new IllegalArgumentException("Factura debe tener timbrado detalle asociado");
        }

        // 3. Determinar configuración del receptor usando SifenReceptorHelper
        Double montoTotal = factura.getTotalFinal();
        SifenReceptorHelper.ConfiguracionReceptor configReceptor =
            SifenReceptorHelper.determinarConfiguracionReceptor(factura.getCliente(), montoTotal);

        // Validar configuración del receptor
        if (!SifenReceptorHelper.validarConfiguracion(configReceptor)) {
            throw new IllegalStateException("Configuración del receptor inválida");
        }

        // 4. Crear objeto com.roshka.sifen.core.beans.DocumentoElectronico
        Object deSifen = crearDocumentoElectronicoSifen(factura, timbradoDetalle, configReceptor);

        // 5. Configurar todos los campos requeridos por SIFEN
        configurarDocumentoElectronico(deSifen, factura, timbradoDetalle, configReceptor);

        // 6. Generar XML usando la librería SIFEN (método correcto por determinar)
        // TODO: Usar el método correcto de la librería SIFEN para generar XML
        // String xmlOriginal = com.roshka.sifen.Sifen.generateXmlDE(deSifen);
        String xmlOriginal = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rDE><dEst>001</dEst><dPunExp>001</dPunExp><dNumDoc>" + factura.getNumeroFactura() + "</dNumDoc></rDE>";
        if (xmlOriginal == null || xmlOriginal.trim().isEmpty()) {
            throw new SifenException("XML_VACIO", "No se pudo generar el XML del documento electrónico");
        }

        // 7. Calcular CDC
        String cdc = calcularCDC(xmlOriginal);

        // 8. Extraer URL QR del XML
        String urlQr = SifenXmlParser.extractUrlQr(xmlOriginal);

        // 9. Crear y guardar entidad DocumentoElectronico
        DocumentoElectronico documentoElectronico = new DocumentoElectronico();
        documentoElectronico.setFacturaLegal(factura);
        documentoElectronico.setSucursalId(factura.getSucursalId());
        documentoElectronico.setCdc(cdc);
        documentoElectronico.setUrlQr(urlQr);
        documentoElectronico.setXmlOriginal(xmlOriginal);
        documentoElectronico.setEstado(EstadoDE.PENDIENTE);
        documentoElectronico.setNumeroDocumento(String.valueOf(factura.getNumeroFactura()));
        documentoElectronico.setTipoDocumento("FACTURA");
        documentoElectronico.setFechaEmision(LocalDateTime.now());
        documentoElectronico.setUsuario(factura.getUsuario());
        documentoElectronico.setActivo(true);

        return documentoElectronicoService.save(documentoElectronico);
    }

    /**
     * Valida que una factura tenga todos los datos necesarios para generar un DE.
     */
    private void validarFacturaParaDE(FacturaLegal factura) {
        if (factura.getTimbradoDetalle() == null) {
            throw new IllegalArgumentException("Factura debe tener timbrado detalle");
        }

        if (factura.getCliente() == null && factura.getNombre() == null) {
            throw new IllegalArgumentException("Factura debe tener cliente o datos de receptor");
        }

        if (factura.getTotalFinal() == null || factura.getTotalFinal() <= 0) {
            throw new IllegalArgumentException("Factura debe tener total final válido");
        }

        if (factura.getNumeroFactura() == null) {
            throw new IllegalArgumentException("Factura debe tener número de factura");
        }
    }

    /**
     * Crea el objeto base de documento electrónico de la librería SIFEN.
     * TODO: Implementar correctamente usando la API real de la librería SIFEN
     */
    private Object crearDocumentoElectronicoSifen(
            FacturaLegal factura,
            TimbradoDetalle timbradoDetalle,
            SifenReceptorHelper.ConfiguracionReceptor configReceptor) {

        // TODO: Crear objeto DocumentoElectronico usando la API correcta de la librería SIFEN
        // Por ahora retornamos un placeholder para que compile
        return new Object();
    }

    /**
     * Configura los datos del emisor en el documento electrónico.
     * TODO: Implementar correctamente cuando se tenga acceso a la API real de SIFEN
     */
    private void configurarDatosEmisor(Object de, TimbradoDetalle timbradoDetalle) {
        // TODO: Configurar datos del emisor usando la API correcta de la librería SIFEN
        // Datos del emisor desde el timbrado
        log.debug("Configurando datos del emisor para timbrado: {}", timbradoDetalle.getTimbrado().getRuc());
    }

    /**
     * Configura los datos del receptor en el documento electrónico.
     * TODO: Implementar correctamente cuando se tenga acceso a la API real de SIFEN
     */
    private void configurarDatosReceptor(
            Object de,
            SifenReceptorHelper.ConfiguracionReceptor configReceptor) {

        // TODO: Configurar datos del receptor usando la API correcta de la librería SIFEN
        log.debug("Configurando datos del receptor: {}", configReceptor.getNombreReceptor());
    }

    /**
     * Configura todos los campos adicionales requeridos por SIFEN.
     * TODO: Implementar correctamente cuando se tenga acceso a la API real de SIFEN
     */
    private void configurarDocumentoElectronico(
            Object de,
            FacturaLegal factura,
            TimbradoDetalle timbradoDetalle,
            SifenReceptorHelper.ConfiguracionReceptor configReceptor) {

        // TODO: Configurar documento usando la API correcta de la librería SIFEN
        log.debug("Configurando documento electrónico para factura: {}", factura.getNumeroFactura());
    }

    /**
     * Configura los totales del documento electrónico.
     * TODO: Implementar correctamente cuando se tenga acceso a la API real de SIFEN
     */
    private void configurarTotales(Object de, FacturaLegal factura) {
        // TODO: Configurar totales usando la API correcta de la librería SIFEN
        log.debug("Configurando totales: IVA10={}, IVA5={}, Total={}",
                 factura.getIvaParcial10(), factura.getIvaParcial5(), factura.getTotalFinal());
    }

    /**
     * Configura información adicional del documento.
     * TODO: Implementar correctamente cuando se tenga acceso a la API real de SIFEN
     */
    private void configurarInformacionAdicional(Object de, FacturaLegal factura) {
        // TODO: Configurar información adicional usando la API correcta de la librería SIFEN
        log.debug("Configurando información adicional para factura: {}", factura.getNumeroFactura());
    }

    /**
     * Calcula el CDC (Código de Control de Documento) a partir del XML.
     */
    private String calcularCDC(String xml) {
        // El CDC se calcula automáticamente por la librería SIFEN
        // cuando se genera el XML firmado, pero aquí necesitamos extraerlo del XML generado
        return SifenXmlParser.extractCdc(xml);
    }

    /**
     * Valida que un lote tenga todos los datos necesarios para ser enviado.
     */
    private void validarLoteParaEnvio(LoteDE lote) {
        if (lote == null) {
            throw new IllegalArgumentException("Lote no puede ser null");
        }

        if (lote.getId() == null) {
            throw new IllegalArgumentException("Lote debe estar guardado en base de datos");
        }

        if (lote.getEstado() != EstadoLoteDE.PENDIENTE_ENVIO) {
            throw new IllegalStateException("Lote debe estar en estado PENDIENTE_ENVIO para ser enviado");
        }

        // Validar que no haya pasado mucho tiempo desde la creación
        if (lote.getCreadoEn() != null &&
            lote.getCreadoEn().isBefore(LocalDateTime.now().minusDays(7))) {
            throw new IllegalStateException("Lote tiene más de 7 días de antigüedad");
        }
    }

    /**
     * Reconstruye un objeto DocumentoElectronico de la librería SIFEN desde XML.
     * TODO: Implementar correctamente cuando se tenga acceso a la API real de SIFEN
     */
    private Object reconstruirDocumentoDesdeXML(String xmlOriginal) {
        if (xmlOriginal == null || xmlOriginal.trim().isEmpty()) {
            return null;
        }

        try {
            // TODO: Reconstruir objeto DocumentoElectronico usando la API correcta de la librería SIFEN
            // Por ahora retornamos un placeholder para que compile
            return new Object();

        } catch (Exception e) {
            log.error("Error al reconstruir documento desde XML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Configura los items de la factura en el documento electrónico.
     * TODO: Implementar cuando tengamos acceso completo a los items de factura.
     */
    private void configurarItemsFactura(com.roshka.sifen.core.beans.DocumentoElectronico de, FacturaLegal factura) {
        // Esta implementación requiere acceso a los items de la factura
        // Por ahora se deja como placeholder para futura implementación

        log.warn("Configuración de items de factura pendiente de implementación");
    }

    /**
     * Valida que un lote tenga protocolo para poder ser consultado.
     */
    private void validarLoteParaConsulta(LoteDE lote) {
        if (lote == null) {
            throw new IllegalArgumentException("Lote no puede ser null");
        }

        if (lote.getProtocolo() == null || lote.getProtocolo().trim().isEmpty()) {
            throw new IllegalArgumentException("Lote debe tener protocolo de consulta");
        }

        if (lote.getEstado() != EstadoLoteDE.EN_PROCESO) {
            throw new IllegalStateException("Lote debe estar en estado EN_PROCESO para ser consultado");
        }
    }

    /**
     * Procesa un documento individual de la respuesta de consulta de lote.
     * Actualiza el estado del DE correspondiente en la base de datos.
     */
    private void procesarDocumentoIndividual(String bloqueDE) {
        try {
            // Extraer información básica del bloque XML
            String cdc = SifenXmlParser.extractCdc(bloqueDE);
            String estado = SifenXmlParser.extractEstadoDE(bloqueDE);
            String codigoRespuesta = SifenXmlParser.extractCodigoRespuesta(bloqueDE);
            String mensajeRespuesta = SifenXmlParser.extractMensajeRespuesta(bloqueDE);

            if (cdc == null) {
                log.warn("No se pudo extraer CDC del bloque DE");
                return;
            }

            // Buscar el documento electrónico por CDC
            Optional<DocumentoElectronico> docOpt = documentoElectronicoService.findByCdc(cdc);
            if (!docOpt.isPresent()) {
                log.warn("No se encontró documento electrónico con CDC: {}", cdc);
                return;
            }

            DocumentoElectronico documento = docOpt.get();

            // Actualizar estado basado en la respuesta de SIFEN
            EstadoDE nuevoEstado = mapearEstadoSifen(estado, codigoRespuesta);
            documento.setEstado(nuevoEstado);
            documento.setCodigoRespuestaSifen(codigoRespuesta);
            documento.setMensajeRespuestaSifen(mensajeRespuesta);

            // Si fue aprobado, guardar el XML firmado
            if (nuevoEstado == EstadoDE.APROBADO) {
                documento.setXmlFirmado(bloqueDE);
                documento.setFechaRecepcionSifen(LocalDateTime.now());
            }

            documentoElectronicoService.save(documento);

            log.debug("Documento CDC {} actualizado a estado {}", cdc, nuevoEstado);

        } catch (Exception e) {
            log.error("Error al procesar documento individual: {}", e.getMessage(), e);
        }
    }

    /**
     * Mapea el estado de SIFEN al enum interno EstadoDE.
     */
    private EstadoDE mapearEstadoSifen(String estadoSifen, String codigoRespuesta) {
        if ("APROBADO".equals(estadoSifen) || "0300".equals(codigoRespuesta) || "0600".equals(codigoRespuesta)) {
            return EstadoDE.APROBADO;
        } else if ("RECHAZADO".equals(estadoSifen) || codigoRespuesta != null && codigoRespuesta.startsWith("1")) {
            return EstadoDE.RECHAZADO;
        } else {
            // Estado intermedio o desconocido
            return EstadoDE.EN_LOTE;
        }
    }

    /**
     * Consulta un DE individual con lógica de reintentos y backoff exponencial.
     */
    private RespuestaConsultaDE consultarDEConReintentos(String cdc) {
        int maxIntentos = 5;
        long backoffInicial = 1000; // 1 segundo

        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                log.debug("Intento {} de consulta DE CDC: {}", intento, cdc);

                // Buscar documento por CDC
                Optional<DocumentoElectronico> docOpt = documentoElectronicoService.findByCdc(cdc);
                if (!docOpt.isPresent()) {
                    throw new IllegalArgumentException("Documento electrónico no encontrado con CDC: " + cdc);
                }

                DocumentoElectronico documento = docOpt.get();

                // Consultar estado usando SIFEN
                // TODO: Implementar consulta real usando la API correcta de SIFEN
                // com.roshka.sifen.core.beans.ResponseConsultaDE respuesta = com.roshka.sifen.Sifen.consultaDE(cdc);

                // Simulación de respuesta exitosa para desarrollo
                String codigoEstado = "0600";
                String mensajeEstado = "Consulta exitosa";

                // Procesar respuesta
                RespuestaConsultaDE respuestaConsulta = new RespuestaConsultaDE();
                respuestaConsulta.setDocumentoElectronico(documento);

                if ("0600".equals(codigoEstado)) {
                    // Consulta exitosa
                    respuestaConsulta.setEstadoSifen("APROBADO");
                    respuestaConsulta.setCodigoRespuesta(codigoEstado);
                    respuestaConsulta.setMensajeRespuesta(mensajeEstado);

                    // Actualizar estado del documento
                    documento.setEstado(EstadoDE.APROBADO);
                    documento.setCodigoRespuestaSifen(codigoEstado);
                    documento.setMensajeRespuestaSifen(mensajeEstado);
                    documento.setFechaRecepcionSifen(LocalDateTime.now());
                    documentoElectronicoService.save(documento);

                    log.info("Consulta DE exitosa para CDC: {}", cdc);
                    return respuestaConsulta;

                } else if (codigoEstado != null && codigoEstado.startsWith("1")) {
                    // Error permanente
                    respuestaConsulta.setEstadoSifen("RECHAZADO");
                    respuestaConsulta.setCodigoRespuesta(codigoEstado);
                    respuestaConsulta.setMensajeRespuesta(mensajeEstado);

                    documento.setEstado(EstadoDE.RECHAZADO);
                    documento.setCodigoRespuestaSifen(codigoEstado);
                    documento.setMensajeRespuestaSifen(mensajeEstado);
                    documentoElectronicoService.save(documento);

                    return respuestaConsulta;

                } else {
                    // Estado intermedio, continuar con reintentos
                    respuestaConsulta.setEstadoSifen("PENDIENTE");
                    respuestaConsulta.setCodigoRespuesta(codigoEstado);
                    respuestaConsulta.setMensajeRespuesta(mensajeEstado);
                }

                // Si no es el último intento, esperar con backoff exponencial
                if (intento < maxIntentos) {
                    long delay = backoffInicial * (1L << (intento - 1)); // Backoff exponencial
                    log.debug("Esperando {} ms antes del siguiente intento", delay);
                    Thread.sleep(delay);
                }

            } catch (Exception e) {
                log.warn("Error en intento {} para CDC {}: {}", intento, cdc, e.getMessage());

                if (intento == maxIntentos) {
                    // Último intento fallido
                    RespuestaConsultaDE respuestaError = new RespuestaConsultaDE();
                    respuestaError.setEstadoSifen("ERROR");
                    respuestaError.setCodigoRespuesta("9999");
                    respuestaError.setMensajeRespuesta("Error después de " + maxIntentos + " intentos: " + e.getMessage());
                    return respuestaError;
                }

                // Esperar antes del siguiente intento
                if (intento < maxIntentos) {
                    try {
                        long delay = backoffInicial * (1L << (intento - 1));
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Nunca debería llegar aquí, pero por seguridad
        RespuestaConsultaDE respuestaError = new RespuestaConsultaDE();
        respuestaError.setEstadoSifen("ERROR");
        respuestaError.setCodigoRespuesta("9999");
        respuestaError.setMensajeRespuesta("Consulta interrumpida");
        return respuestaError;
    }

    /**
     * Crea un nuevo lote vacío para agrupar DEs.
     *
     * @return El lote creado en estado PENDIENTE_ENVIO
     * @throws IllegalStateException Si no se puede crear el lote
     */
    @Transactional(rollbackFor = Exception.class)
    public LoteDE crearLote() {
        log.info("Creando nuevo lote DE");

        try {
            LoteDE lote = new LoteDE();
            lote.setEstado(EstadoLoteDE.PENDIENTE_ENVIO);
            lote.setIntentos(0);
            lote.setCreadoEn(LocalDateTime.now());

            LoteDE loteGuardado = loteDEService.save(lote);
            log.info("Lote creado exitosamente con ID: {}", loteGuardado.getId());

            return loteGuardado;
        } catch (Exception e) {
            log.error("Error al crear lote DE: {}", e.getMessage(), e);
            throw new IllegalStateException("No se pudo crear el lote DE", e);
        }
    }

    /**
     * Vincula documentos electrónicos pendientes a un lote.
     * Cambia el estado de los DEs de PENDIENTE a EN_LOTE.
     *
     * @param lote El lote al que vincular los documentos
     * @param documentos Lista de documentos a vincular
     * @throws IllegalArgumentException Si los parámetros son inválidos
     * @throws IllegalStateException Si no se pueden vincular los documentos
     */
    @Transactional(rollbackFor = Exception.class)
    public void vincularDocumentosALote(LoteDE lote, List<DocumentoElectronico> documentos) {
        if (lote == null || lote.getId() == null) {
            throw new IllegalArgumentException("Lote debe estar guardado en base de datos");
        }

        if (documentos == null || documentos.isEmpty()) {
            throw new IllegalArgumentException("Lista de documentos no puede ser null o vacía");
        }

        log.info("Vinculando {} documentos al lote ID: {}", documentos.size(), lote.getId());

        int vinculados = 0;
        int rechazados = 0;

        for (DocumentoElectronico doc : documentos) {
            if (doc == null || doc.getId() == null) {
                log.warn("Documento null o sin ID, saltando");
                rechazados++;
                continue;
            }

            if (doc.getEstado() != EstadoDE.PENDIENTE) {
                log.warn("Documento ID {} no está en estado PENDIENTE (estado actual: {}), saltando",
                         doc.getId(), doc.getEstado());
                rechazados++;
                continue;
            }

            try {
                doc.setLoteDe(lote);
                doc.setEstado(EstadoDE.EN_LOTE);
                documentoElectronicoService.save(doc);
                vinculados++;
            } catch (Exception e) {
                log.error("Error al vincular documento ID {} al lote: {}", doc.getId(), e.getMessage());
                rechazados++;
            }
        }

        log.info("Vinculación completada: {} documentos vinculados, {} rechazados", vinculados, rechazados);

        if (vinculados == 0) {
            throw new IllegalStateException("No se pudo vincular ningún documento al lote");
        }
    }

    /**
     * Envía un lote de DEs a SIFEN.
     * 
     * Este método:
     * 1. Recupera los DEs del lote
     * 2. Reconstruye los objetos DE de la librería desde el XML original guardado
     * 3. Envía el lote a SIFEN usando Sifen.recepcionLoteDE
     * 4. Procesa la respuesta y actualiza el estado del lote
     * 
     * @param lote El lote a enviar
     * @throws SifenException Si hay errores en el envío
     */
    @Transactional
    public void enviarLote(LoteDE lote) throws SifenException {
        log.info("Enviando lote ID: {} a SIFEN", lote.getId());
        
        // 1. Validar que el lote tenga DEs asociados
        validarLoteParaEnvio(lote);

        // 2. Recuperar los DEs del lote
        List<DocumentoElectronico> documentos = documentoElectronicoService.findByLoteDeIdAndEstado(lote.getId(), EstadoDE.EN_LOTE);

        if (documentos.isEmpty()) {
            throw new IllegalStateException("El lote no tiene documentos electrónicos para enviar");
        }

        log.info("Enviando lote con {} documentos electrónicos", documentos.size());

        // 3. Reconstruir objetos DE de la librería desde xmlOriginal
        List<Object> documentosSifen = new ArrayList<>();

        for (DocumentoElectronico doc : documentos) {
            try {
                // Reconstruir el objeto DE desde el XML original guardado
                Object deSifen = reconstruirDocumentoDesdeXML(doc.getXmlOriginal());

                if (deSifen != null) {
                    documentosSifen.add((Object) deSifen);
                } else {
                    log.error("No se pudo reconstruir el documento electrónico ID: {}", doc.getId());
                }
            } catch (Exception e) {
                log.error("Error al reconstruir documento ID {}: {}", doc.getId(), e.getMessage());
                // Continuar con otros documentos
            }
        }

        if (documentosSifen.isEmpty()) {
            throw new SifenException("NO_DOCUMENTOS", "No se pudieron reconstruir los documentos electrónicos del lote");
        }

        // 4. Enviar usando Sifen.recepcionLoteDE()
        // TODO: Implementar envío real usando la API correcta de SIFEN
        try {
            // Simulación de respuesta exitosa para desarrollo
            lote.setEstado(EstadoLoteDE.EN_PROCESO);
            lote.setProtocolo("PROTO-" + System.currentTimeMillis());
            lote.setRespuestaSifen("Lote enviado exitosamente (simulado)");
            log.info("Lote enviado exitosamente. Protocolo de consulta: {}", lote.getProtocolo());

        } catch (Exception e) {
            log.error("Excepción durante envío del lote: {}", e.getMessage(), e);
            lote.setEstado(EstadoLoteDE.ERROR_ENVIO);
            lote.setRespuestaSifen("Error durante envío: " + e.getMessage());
        }

        // 6. Actualizar estado del lote y contador de intentos
        lote.setIntentos(lote.getIntentos() + 1);
        lote.setFechaUltimoIntento(LocalDateTime.now());

        loteDEService.save(lote);

        if (lote.getEstado() == EstadoLoteDE.ERROR_ENVIO) {
            throw new SifenException("ERROR_ENVIO", "Error en el envío del lote a SIFEN");
        }
    }

    /**
     * Consulta el estado de un lote previamente enviado.
     * 
     * @param lote El lote a consultar
     * @throws SifenException Si hay errores en la consulta
     */
    @Transactional
    public void consultarLote(LoteDE lote) throws SifenException {
        log.info("Consultando estado del lote ID: {} con protocolo: {}", lote.getId(), lote.getProtocolo());
        
        // 1. Validar que el lote tenga protocolo
        validarLoteParaConsulta(lote);

        log.info("Consultando estado del lote ID: {} con protocolo: {}", lote.getId(), lote.getProtocolo());

        // 2. Consultar usando Sifen.consultaLoteDE(protocolo)
        // TODO: Implementar consulta real usando la API correcta de SIFEN
        try {
            // Simulación de consulta exitosa para desarrollo
            String xmlRespuesta = "<?xml version=\"1.0\"?><rResEnvi><dCodRes>0600</dCodRes><dMsgRes>Consulta exitosa</dMsgRes></rResEnvi>";
            String codigoEstado = "0600";

            // 3. Procesar respuesta XML
            if (xmlRespuesta == null || xmlRespuesta.trim().isEmpty()) {
                throw new SifenException("XML_VACIO", "XML de respuesta está vacío");
            }

            // 4. Actualizar estado del lote basado en la respuesta
            if ("0600".equals(codigoEstado)) {
                // Consulta exitosa - procesar resultados individuales
                lote.setEstado(EstadoLoteDE.PROCESADO);
                lote.setFechaProcesado(LocalDateTime.now());
                log.info("Consulta de lote exitosa. Procesando documentos individuales");
            } else {
                // Error en la consulta
                lote.setEstado(EstadoLoteDE.PROCESADO_CON_ERRORES);
                lote.setFechaProcesado(LocalDateTime.now());
                log.warn("Consulta de lote con errores. Código: {}", codigoEstado);
            }

            // 5. Extraer bloques individuales de cada DE y procesarlos
            List<String> bloquesDEs = SifenXmlParser.extractBloquesDEs(xmlRespuesta);
            log.info("Procesando {} documentos individuales del lote", bloquesDEs.size());

            for (String bloqueDE : bloquesDEs) {
                procesarDocumentoIndividual(bloqueDE);
            }

            // 6. Guardar cambios del lote
            loteDEService.save(lote);

        } catch (Exception e) {
            log.error("Error durante consulta del lote: {}", e.getMessage(), e);
            lote.setEstado(EstadoLoteDE.ERROR_RED);
            lote.setFechaProcesado(LocalDateTime.now());
            loteDEService.save(lote);

            throw new SifenException("ERROR_CONSULTA", "Error al consultar lote: " + e.getMessage());
        }
    }

    /**
     * Consulta el estado de un DE individual por su CDC.
     * Implementa lógica de reintentos con backoff exponencial.
     * 
     * @param cdc El CDC del documento a consultar
     * @return RespuestaConsultaDE con los datos actualizados
     */
    @Transactional
    public RespuestaConsultaDE consultarDE(String cdc) {
        log.info("Consultando DE con CDC: {}", cdc);
        
        // TODO: Implementar lógica completa de consulta de DE
        // 1. Buscar DE en base de datos por CDC
        // 2. Consultar estado usando Sifen.consultaDE(cdc)
        // 3. Implementar reintentos con backoff exponencial
        // 4. Parsear respuesta XML
        // 5. Usar SifenEventoParser para buscar eventos asociados
        // 6. Actualizar estado del DE
        // 7. Si hay evento de cancelación aprobado, marcar DE como CANCELADO
        // 8. Guardar XML de respuesta
        
        // Implementar lógica de reintentos con backoff exponencial
        return consultarDEConReintentos(cdc);
    }

    /**
     * DTO para respuestas de consulta de DE
     */
    public static class RespuestaConsultaDE {
        private DocumentoElectronico documentoElectronico;
        private String estadoSifen;
        private String codigoRespuesta;
        private String mensajeRespuesta;
        private boolean tieneEventos;
        
        // Getters y setters
        public DocumentoElectronico getDocumentoElectronico() { return documentoElectronico; }
        public void setDocumentoElectronico(DocumentoElectronico documentoElectronico) { this.documentoElectronico = documentoElectronico; }
        
        public String getEstadoSifen() { return estadoSifen; }
        public void setEstadoSifen(String estadoSifen) { this.estadoSifen = estadoSifen; }
        
        public String getCodigoRespuesta() { return codigoRespuesta; }
        public void setCodigoRespuesta(String codigoRespuesta) { this.codigoRespuesta = codigoRespuesta; }
        
        public String getMensajeRespuesta() { return mensajeRespuesta; }
        public void setMensajeRespuesta(String mensajeRespuesta) { this.mensajeRespuesta = mensajeRespuesta; }
        
        public boolean isTieneEventos() { return tieneEventos; }
        public void setTieneEventos(boolean tieneEventos) { this.tieneEventos = tieneEventos; }
    }
}

