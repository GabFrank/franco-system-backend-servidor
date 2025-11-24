package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.EventoInutilizacionDE;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
import com.franco.dev.service.financiero.EventoInutilizacionDEService;
import com.franco.dev.service.financiero.EventoInutilizacionDEDocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.sifen.util.SifenReceptorHelper;
import com.roshka.sifen.Sifen;
import com.roshka.sifen.core.beans.EventosDE;
import com.roshka.sifen.core.beans.response.RespuestaConsultaDE;
import com.roshka.sifen.core.beans.response.RespuestaRecepcionEvento;
import com.roshka.sifen.core.exceptions.SifenException;
import com.roshka.sifen.core.fields.request.event.TgGroupTiEvt;
import com.roshka.sifen.core.fields.request.event.TrGeVeCan;
import com.roshka.sifen.core.fields.request.event.TrGeVeInu;
import com.roshka.sifen.core.fields.request.event.TrGeVeNotRec;
import com.roshka.sifen.core.fields.request.event.TrGesEve;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TTiDE;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
public class SifenEventoService {

    private final DocumentoElectronicoService documentoElectronicoService;
    private final EventoCancelacionDEService eventoCancelacionDEService;
    private final EventoInutilizacionDEService eventoInutilizacionDEService;
    private final EventoInutilizacionDEDocumentoElectronicoService eventoInutilizacionDEDocumentoElectronicoService;
    private final EventoNominacionDEService eventoNominacionDEService;
    private final FacturaLegalService facturaLegalService;

    public SifenEventoService(
            DocumentoElectronicoService documentoElectronicoService,
            EventoCancelacionDEService eventoCancelacionDEService,
            EventoInutilizacionDEService eventoInutilizacionDEService,
            EventoInutilizacionDEDocumentoElectronicoService eventoInutilizacionDEDocumentoElectronicoService,
            EventoNominacionDEService eventoNominacionDEService,
            FacturaLegalService facturaLegalService) {
        this.documentoElectronicoService = documentoElectronicoService;
        this.eventoCancelacionDEService = eventoCancelacionDEService;
        this.eventoInutilizacionDEService = eventoInutilizacionDEService;
        this.eventoInutilizacionDEDocumentoElectronicoService = eventoInutilizacionDEDocumentoElectronicoService;
        this.eventoNominacionDEService = eventoNominacionDEService;
        this.facturaLegalService = facturaLegalService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = {SifenException.class, IllegalStateException.class, IllegalArgumentException.class})
    public RespuestaRecepcionEvento cancelarDE(String cdc, String motivo) throws SifenException {
        log.info("🚫 [INICIO] Cancelando DE con CDC: {}", cdc);
        log.info("   Motivo: {}", motivo);

        try {
            log.info("   [PASO 1] Buscando documento electrónico en BD...");
            DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));
            log.info("   ✅ DE encontrado - ID: {}, Sucursal: {}, Estado: {}", de.getId(), de.getSucursalId(), de.getEstado());

            log.info("   [PASO 2] Verificando si ya tiene cancelación aprobada...");
            if (eventoCancelacionDEService.tieneCancelacionAprobada(de.getId(), de.getSucursalId())) {
                log.warn("⚠️ El DE ya tiene un evento de cancelación APROBADO");
                log.warn("   Estado del DE: {}", de.getEstado());
                throw new IllegalStateException("El DE ya fue cancelado exitosamente. No se puede cancelar nuevamente.");
            }
            log.info("   ✅ No tiene cancelación aprobada previa");

            log.info("   [PASO 3] Buscando eventos activos previos...");
            List<EventoCancelacionDE> eventosActivos = eventoCancelacionDEService.findActivosByCdcDocumento(cdc);
            if (!eventosActivos.isEmpty()) {
                log.info("   🔄 Se encontraron {} evento(s) previo(s) - realizando reintento automático", eventosActivos.size());
                for (EventoCancelacionDE eventoAnterior : eventosActivos) {
                    eventoAnterior.setActivo(false);
                    eventoCancelacionDEService.save(eventoAnterior);
                    log.info("      📝 Evento anterior ID {} marcado como inactivo (estado: {})", eventoAnterior.getId(), eventoAnterior.getEstado());
                }
            } else {
                log.info("   ✅ No hay eventos activos previos");
            }

            log.info("   [PASO 4] Construyendo XML del evento de cancelación...");
            TrGeVeCan cancelacion = new TrGeVeCan();
            cancelacion.setId(cdc);
            cancelacion.setmOtEve(motivo);

            TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
            tipoEvento.setrGeVeCan(cancelacion);

            TrGesEve gestionEvento = new TrGesEve();
            int numeroRandom = new Random().nextInt(99999999) + 1;
            String eventoId = String.valueOf(numeroRandom);
            LocalDateTime fechaFirma = LocalDateTime.now();
            gestionEvento.setId(eventoId);
            gestionEvento.setdFecFirma(fechaFirma);
            gestionEvento.setgGroupTiEvt(tipoEvento);

            List<TrGesEve> listaEventos = new ArrayList<>();
            listaEventos.add(gestionEvento);

            EventosDE eventosDE = new EventosDE();
            eventosDE.setrGesEveList(listaEventos);
            log.info("   ✅ XML construido - Evento ID: {}", eventoId);

            log.info("   [PASO 5] Creando registro de EventoCancelacionDE...");
            EventoCancelacionDE eventoCancelacion = new EventoCancelacionDE();
            eventoCancelacion.setDocumentoElectronico(de);
            eventoCancelacion.setDocumentoElectronicoId(de.getId());
            eventoCancelacion.setSucursal(de.getSucursal());
            eventoCancelacion.setSucursalId(de.getSucursalId());
            eventoCancelacion.setEventoId(eventoId);
            eventoCancelacion.setFechaFirma(fechaFirma);
            eventoCancelacion.setCdcDocumento(cdc);
            eventoCancelacion.setMotivoCancelacion(motivo);
            eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
            eventoCancelacion.setActivo(true);
            log.info("   ✅ Registro de evento creado (aún no guardado en BD)");

            log.info("   [PASO 6] 📤 Enviando evento de cancelación a SIFEN...");
            RespuestaRecepcionEvento respuesta = null;
            try {
                respuesta = Sifen.recepcionEvento(eventosDE);
                log.info("   ✅ Respuesta recibida de SIFEN");
            } catch (Exception e) {
                log.error("   ❌ ERROR al enviar a SIFEN: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
                throw e;
            }

            log.info("   [PASO 7] Procesando respuesta de SIFEN...");
            String xmlRespuesta = respuesta.getRespuestaBruta();
            log.info("   📄 Tamaño respuesta XML: {} bytes", xmlRespuesta != null ? xmlRespuesta.length() : 0);
            
            String codigoRespuesta = extraerValorXML(xmlRespuesta, "<dCodRes>", "</dCodRes>");
            if (codigoRespuesta == null) {
                codigoRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dCodRes>", "</ns2:dCodRes>");
            }
            String mensajeRespuesta = extraerValorXML(xmlRespuesta, "<dMsgRes>", "</dMsgRes>");
            if (mensajeRespuesta == null) {
                mensajeRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
            }
            log.info("   📥 Respuesta recibida - Código: {}", codigoRespuesta);
            log.info("   📥 Mensaje: {}", mensajeRespuesta);

            eventoCancelacion.setRespuestaBruta(xmlRespuesta);
            eventoCancelacion.setCodigoRespuesta(codigoRespuesta);
            eventoCancelacion.setMensajeRespuesta(mensajeRespuesta);

            String estadoResultado = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");
            if (estadoResultado == null) {
                estadoResultado = extraerValorXML(xmlRespuesta, "<ns2:dEstRes>", "</ns2:dEstRes>");
            }
            log.info("   📊 Estado del evento en SIFEN: {}", estadoResultado);

            String protocolo = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
            if (protocolo == null) {
                protocolo = extraerValorXML(xmlRespuesta, "<ns2:dProtAut>", "</ns2:dProtAut>");
            }
            if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                eventoCancelacion.setProtocoloAutorizacion(protocolo);
                log.info("   📋 Protocolo: {}", protocolo);
            }

            log.info("   [PASO 8] Determinando estado del evento...");
            // Variable para controlar si debemos lanzar excepción
            String errorMessage = null;

            if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
                log.info("   🎯 Estado: APROBADO");
                eventoCancelacion.setEstado(EstadoEvento.APROBADO);
                eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
                de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
                de.setCodigoRespuestaSifen(codigoRespuesta);
                de.setMensajeRespuestaSifen(mensajeRespuesta);
                try {
                    documentoElectronicoService.save(de);
                    log.info("   ✅ Evento APROBADO - DE actualizado a estado CANCELADO");
                } catch (Exception e) {
                    log.error("   ❌ ERROR al guardar DE con estado CANCELADO: {}", e.getMessage());
                    e.printStackTrace();
                }
                log.info("   📋 Código SIFEN: {} - {}", codigoRespuesta, mensajeRespuesta);
            } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
                log.info("   🎯 Estado: RECHAZADO");
                eventoCancelacion.setEstado(EstadoEvento.RECHAZADO);
                eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
                de.setCodigoRespuestaSifen(codigoRespuesta);
                de.setMensajeRespuestaSifen(mensajeRespuesta);
                try {
                    documentoElectronicoService.save(de);
                } catch (Exception e) {
                    log.error("   ❌ ERROR al guardar DE con respuesta de rechazo: {}", e.getMessage());
                    e.printStackTrace();
                }
                log.error("   ❌ Evento RECHAZADO por SIFEN");
                log.error("   📋 Código: {} - {}", codigoRespuesta, mensajeRespuesta);
                log.error("   ℹ️ El DE mantiene su estado actual: {}", de.getEstado());
                errorMessage = "SIFEN rechazó la cancelación: " + mensajeRespuesta;
            } else if (estadoResultado == null || estadoResultado.isEmpty()) {
                log.info("   🎯 Estado resultado es null o vacío - evaluando código de respuesta");
                if ("0300".equals(codigoRespuesta)) {
                    eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
                    log.info("   ✅ Evento recibido (código 0300) - pendiente de procesamiento");
                } else if ("0600".equals(codigoRespuesta)) {
                    if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                        eventoCancelacion.setEstado(EstadoEvento.APROBADO);
                        eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
                        de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
                        try {
                            documentoElectronicoService.save(de);
                            log.info("   ✅ Evento APROBADO (código 0600 + protocolo) - DE actualizado a CANCELADO");
                        } catch (Exception e) {
                            log.error("   ❌ ERROR al guardar DE con estado CANCELADO (0600): {}", e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
                        log.info("   ✅ Evento registrado (código 0600) - estado pendiente");
                    }
                } else {
                    eventoCancelacion.setEstado(EstadoEvento.ERROR_ENVIO);
                    log.error("   ❌ Error en envío - Código: {} - {}", codigoRespuesta, mensajeRespuesta);
                    errorMessage = "Error al enviar evento: " + codigoRespuesta + " - " + mensajeRespuesta;
                }
            } else {
                log.info("   🎯 Estado desconocido: {}", estadoResultado);
                eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
                log.warn("   ⚠️ Estado desconocido: {} - marcando como PENDIENTE", estadoResultado);
            }

            log.info("   [PASO 9] Guardando evento en BD...");
            try {
                eventoCancelacionDEService.save(eventoCancelacion);
                log.info("   ✅ 💾 Evento guardado en BD - ID: {}, Estado: {}", eventoCancelacion.getId(), eventoCancelacion.getEstado());
            } catch (Exception e) {
                log.error("   ❌ ERROR CRÍTICO al guardar EventoCancelacionDE: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error al guardar evento de cancelación en BD", e);
            }

            // Si hubo error, lanzar excepción DESPUÉS de guardar
            if (errorMessage != null) {
                log.warn("   ⚠️ Lanzando excepción por error: {}", errorMessage);
                throw new IllegalStateException(errorMessage);
            }

            log.info("   ✅ [FIN] Proceso de cancelación completado exitosamente");
            return respuesta;
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("❌ [ERROR CONTROLADO] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } catch (SifenException e) {
            log.error("❌ [ERROR SIFEN] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            log.error("❌ [ERROR NO ESPERADO] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error inesperado en cancelación de DE", e);
        }
    }

    private String extraerValorXML(String xml, String tagInicio, String tagFin) {
        try {
            int inicio = xml.indexOf(tagInicio);
            if (inicio == -1) return null;
            inicio += tagInicio.length();
            int fin = xml.indexOf(tagFin, inicio);
            if (fin == -1) return null;
            return xml.substring(inicio, fin).trim();
        } catch (Exception e) {
            log.error("Error al extraer valor XML: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = {SifenException.class, IllegalStateException.class, IllegalArgumentException.class})
    public RespuestaRecepcionEvento inutilizarNumeros(
            Timbrado timbrado,
            String establecimiento,
            String puntoExpedicion,
            int numeroInicio,
            int numeroFin,
            TTiDE tipoDE,
            String motivo,
            Long sucursalId,
            Long timbradoDetalleId) throws SifenException {

        log.info("📝 [INICIO] Inutilizando números de documentos");
        log.info("   Timbrado: {}", timbrado.getNumero());
        log.info("   Establecimiento: {}", establecimiento);
        log.info("   Punto Expedición: {}", puntoExpedicion);
        log.info("   Rango: {} - {}", numeroInicio, numeroFin);
        log.info("   Tipo DE: {}", tipoDE);
        log.info("   Motivo: {}", motivo);
        log.info("   Sucursal ID: {}", sucursalId);
        log.info("   Timbrado Detalle ID: {}", timbradoDetalleId);

        try {
        if (numeroInicio > numeroFin) {
            throw new IllegalArgumentException("Número inicial (" + numeroInicio + ") no puede ser mayor que número final (" + numeroFin + ")");
        }

            log.info("   [PASO 1] Construyendo XML del evento de inutilización...");
        TrGeVeInu inutilizacion = new TrGeVeInu();
        inutilizacion.setdNumTim(Integer.parseInt(timbrado.getNumero()));
        inutilizacion.setdEst(establecimiento);
        inutilizacion.setdPunExp(puntoExpedicion);
        inutilizacion.setdNumIn(String.valueOf(numeroInicio));
        inutilizacion.setdNumFin(String.valueOf(numeroFin));
        inutilizacion.setiTiDE(tipoDE);
        inutilizacion.setmOtEve(motivo);

        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeInu(inutilizacion);

            int numeroRandom = new Random().nextInt(99999999) + 1;
            String eventoId = String.valueOf(numeroRandom);
            LocalDateTime fechaFirma = LocalDateTime.now();
        TrGesEve gestionEvento = new TrGesEve();
        gestionEvento.setId(eventoId);
            gestionEvento.setdFecFirma(fechaFirma);
        gestionEvento.setgGroupTiEvt(tipoEvento);

        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);

        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);
            log.info("   ✅ XML construido - Evento ID: {}", eventoId);

            log.info("   [PASO 2] Creando registro de EventoInutilizacionDE...");
            EventoInutilizacionDE eventoInutilizacion = new EventoInutilizacionDE();
            eventoInutilizacion.setTimbrado(timbrado);
            eventoInutilizacion.setTimbradoId(timbrado.getId());
            eventoInutilizacion.setTimbradoDetalleId(timbradoDetalleId);
            eventoInutilizacion.setSucursalId(sucursalId);
            eventoInutilizacion.setEventoId(eventoId);
            eventoInutilizacion.setFechaFirma(fechaFirma);
            eventoInutilizacion.setEstablecimiento(establecimiento);
            eventoInutilizacion.setPuntoExpedicion(puntoExpedicion);
            eventoInutilizacion.setNumeroInicio(numeroInicio);
            eventoInutilizacion.setNumeroFin(numeroFin);
            eventoInutilizacion.setTipoDE(tipoDE.name());
            eventoInutilizacion.setMotivoInutilizacion(motivo);
            eventoInutilizacion.setEstado(EstadoEvento.PENDIENTE);
            eventoInutilizacion.setActivo(true);
            log.info("   ✅ Registro de evento creado (aún no guardado en BD)");

            log.info("   [PASO 3] 📤 Enviando evento de inutilización a SIFEN...");
            RespuestaRecepcionEvento respuesta = null;
            try {
                respuesta = Sifen.recepcionEvento(eventosDE);
                log.info("   ✅ Respuesta recibida de SIFEN");
            } catch (Exception e) {
                log.error("   ❌ ERROR al enviar a SIFEN: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
                throw e;
            }

            log.info("   [PASO 4] Procesando respuesta de SIFEN...");
            String xmlRespuesta = respuesta.getRespuestaBruta();
            log.info("   📄 Tamaño respuesta XML: {} bytes", xmlRespuesta != null ? xmlRespuesta.length() : 0);
            
            String codigoRespuesta = extraerValorXML(xmlRespuesta, "<dCodRes>", "</dCodRes>");
            if (codigoRespuesta == null) {
                codigoRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dCodRes>", "</ns2:dCodRes>");
            }
            String mensajeRespuesta = extraerValorXML(xmlRespuesta, "<dMsgRes>", "</dMsgRes>");
            if (mensajeRespuesta == null) {
                mensajeRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
            }
            log.info("   📥 Respuesta recibida - Código: {}", codigoRespuesta);
            log.info("   📥 Mensaje: {}", mensajeRespuesta);

            eventoInutilizacion.setRespuestaBruta(xmlRespuesta);
            eventoInutilizacion.setCodigoRespuesta(codigoRespuesta);
            eventoInutilizacion.setMensajeRespuesta(mensajeRespuesta);

            String estadoResultado = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");
            if (estadoResultado == null) {
                estadoResultado = extraerValorXML(xmlRespuesta, "<ns2:dEstRes>", "</ns2:dEstRes>");
            }
            log.info("   📊 Estado del evento en SIFEN: {}", estadoResultado);

            String protocolo = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
            if (protocolo == null) {
                protocolo = extraerValorXML(xmlRespuesta, "<ns2:dProtAut>", "</ns2:dProtAut>");
            }
            if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                eventoInutilizacion.setProtocoloAutorizacion(protocolo);
                log.info("   📋 Protocolo: {}", protocolo);
            }

            log.info("   [PASO 5] Determinando estado del evento...");
            String errorMessage = null;

            if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
                log.info("   🎯 Estado: APROBADO");
                eventoInutilizacion.setEstado(EstadoEvento.APROBADO);
                eventoInutilizacion.setFechaProcesamiento(LocalDateTime.now());
                log.info("   ✅ Evento APROBADO por SIFEN");
                log.info("   📋 Código SIFEN: {} - {}", codigoRespuesta, mensajeRespuesta);
            } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
                log.info("   🎯 Estado: RECHAZADO");
                eventoInutilizacion.setEstado(EstadoEvento.RECHAZADO);
                eventoInutilizacion.setFechaProcesamiento(LocalDateTime.now());
                log.warn("   ⚠️ Evento RECHAZADO por SIFEN (no es un error, es una respuesta válida)");
                log.warn("   📋 Código: {} - {}", codigoRespuesta, mensajeRespuesta);
                // No lanzar excepción para rechazos - es una respuesta válida de SIFEN
            } else if (estadoResultado == null || estadoResultado.isEmpty()) {
                log.info("   🎯 Estado resultado es null o vacío - evaluando código de respuesta");
                if ("0300".equals(codigoRespuesta)) {
                    eventoInutilizacion.setEstado(EstadoEvento.PENDIENTE);
                    log.info("   ✅ Evento recibido (código 0300) - pendiente de procesamiento");
                } else if ("0600".equals(codigoRespuesta)) {
                    if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                        eventoInutilizacion.setEstado(EstadoEvento.APROBADO);
                        eventoInutilizacion.setFechaProcesamiento(LocalDateTime.now());
                        log.info("   ✅ Evento APROBADO (código 0600 + protocolo)");
                    } else {
                        eventoInutilizacion.setEstado(EstadoEvento.PENDIENTE);
                        log.info("   ✅ Evento registrado (código 0600) - estado pendiente");
                    }
                } else {
                    eventoInutilizacion.setEstado(EstadoEvento.ERROR_ENVIO);
                    log.error("   ❌ Error en envío - Código: {} - {}", codigoRespuesta, mensajeRespuesta);
                    errorMessage = "Error al enviar evento: " + codigoRespuesta + " - " + mensajeRespuesta;
                }
            } else {
                log.info("   🎯 Estado desconocido: {}", estadoResultado);
                eventoInutilizacion.setEstado(EstadoEvento.PENDIENTE);
                log.warn("   ⚠️ Estado desconocido: {} - marcando como PENDIENTE", estadoResultado);
            }

            log.info("   [PASO 6] Guardando evento en BD...");
            try {
                eventoInutilizacionDEService.save(eventoInutilizacion);
                log.info("   ✅ 💾 Evento guardado en BD - ID: {}, Estado: {}", eventoInutilizacion.getId(), eventoInutilizacion.getEstado());
            } catch (Exception e) {
                log.error("   ❌ ERROR CRÍTICO al guardar EventoInutilizacionDE: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error al guardar evento de inutilización en BD", e);
            }

            log.info("   [PASO 7] Buscando documentos electrónicos afectados en el rango {} - {}...", numeroInicio, numeroFin);
            try {
                // Buscar facturas legales en el rango
                List<FacturaLegal> facturasAfectadas = facturaLegalService
                        .findByTimbradoDetalleIdAndSucursalIdAndNumeroFacturaBetween(
                                timbradoDetalleId,
                                sucursalId,
                                numeroInicio,
                                numeroFin
                        );
                log.info("   📋 Facturas encontradas en el rango: {}", facturasAfectadas.size());

                // Obtener documentos electrónicos relacionados
                List<DocumentoElectronico> documentosAfectados = new ArrayList<>();
                for (FacturaLegal factura : facturasAfectadas) {
                    Optional<DocumentoElectronico> docOpt = documentoElectronicoService
                            .findByFacturaLegalId(factura.getId(), sucursalId);
                    if (docOpt.isPresent()) {
                        documentosAfectados.add(docOpt.get());
                        log.debug("   📄 Documento electrónico encontrado - ID: {}, CDC: {}", 
                                docOpt.get().getId(), docOpt.get().getCdc());
                    }
                }
                log.info("   📄 Documentos electrónicos encontrados: {}", documentosAfectados.size());

                // Vincular documentos con el evento
                if (!documentosAfectados.isEmpty()) {
                    int relacionesCreadas = eventoInutilizacionDEDocumentoElectronicoService
                            .crearRelaciones(
                                    eventoInutilizacion.getId(),
                                    eventoInutilizacion.getSucursalId(),
                                    documentosAfectados
                            );
                    log.info("   ✅ {} relaciones creadas entre el evento y documentos electrónicos", relacionesCreadas);
                } else {
                    log.info("   ℹ️ No se encontraron documentos electrónicos en el rango inutilizado");
                }
            } catch (Exception e) {
                log.error("   ⚠️ ERROR al vincular documentos electrónicos (no crítico): {} - {}", 
                        e.getClass().getSimpleName(), e.getMessage());
                e.printStackTrace();
                // No lanzar excepción - la vinculación es opcional, el evento ya está guardado
            }

            // Solo lanzar excepción para errores reales (ERROR_ENVIO), no para rechazos de SIFEN
            if (errorMessage != null) {
                log.warn("   ⚠️ Lanzando excepción por error: {}", errorMessage);
                throw new IllegalStateException(errorMessage);
            }

            log.info("   ✅ [FIN] Proceso de inutilización completado exitosamente");
        return respuesta;
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("❌ [ERROR CONTROLADO] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } catch (SifenException e) {
            log.error("❌ [ERROR SIFEN] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            log.error("❌ [ERROR NO ESPERADO] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error inesperado en inutilización de números", e);
        }
    }

    @Transactional
    public RespuestaRecepcionEvento nominarReceptor(String cdc, Cliente cliente) throws SifenException {
        log.info("👤 Nominando receptor para DE con CDC: {}", cdc);
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente no puede ser null para nominación");
        }
        log.info("   Cliente: {} (ID: {})", cliente.getPersona() != null ? cliente.getPersona().getNombre() : "null", cliente.getId());

        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));

        FacturaLegal factura = de.getFacturaLegal();
        if (factura == null) {
            throw new IllegalArgumentException("DE sin factura asociada");
        }

        if (eventoNominacionDEService.tieneNominacionAprobada(de.getId(), de.getSucursalId())) {
            log.warn("⚠️ El DE ya tiene un evento de nominación APROBADO");
            throw new IllegalStateException("El DE ya fue nominado exitosamente. No se puede nominar nuevamente.");
        }

        List<EventoNominacionDE> eventosActivos = eventoNominacionDEService.findActivosByCdcDocumento(cdc);
        if (!eventosActivos.isEmpty()) {
            log.info("   🔄 Se encontraron {} evento(s) previo(s) - realizando reintento automático", eventosActivos.size());
            for (EventoNominacionDE eventoAnterior : eventosActivos) {
                eventoAnterior.setActivo(false);
                eventoNominacionDEService.save(eventoAnterior);
                log.info("      📝 Evento anterior ID {} marcado como inactivo (estado: {})", eventoAnterior.getId(), eventoAnterior.getEstado());
            }
        }

        BigDecimal totalFactura = BigDecimal.valueOf(factura.getTotalFinal());
        LocalDateTime fechaFirma = LocalDateTime.now();
        LocalDateTime fechaRecepcion = LocalDateTime.now();
        log.info("   Total: {} (obtenido desde factura ID: {})", totalFactura, factura.getId());

        SifenReceptorHelper.ConfiguracionReceptor config = SifenReceptorHelper.determinarConfiguracionReceptor(cliente, factura.getTotalFinal());
        TrGeVeNotRec nominacion = new TrGeVeNotRec();
        nominacion.setId(cdc);
        nominacion.setdFecEmi(factura.getFecha());
        nominacion.setdFecRecep(fechaRecepcion);
        nominacion.setdTotalGs(totalFactura);
        nominacion.setdNomRec(config.getNombreReceptor());

        String tipoReceptor;
        String documentoReceptor;
        if (config.getNaturalezaReceptor() == TiNatRec.CONTRIBUYENTE) {
            nominacion.setiTipRec(TiNatRec.CONTRIBUYENTE);
            nominacion.setdRucRec(config.getNumeroDocumento());
            nominacion.setdDVRec(String.valueOf(config.getDigitoVerificador()));
            tipoReceptor = "CONTRIBUYENTE";
            documentoReceptor = config.getNumeroDocumento() + "-" + config.getDigitoVerificador();
            log.info("   Tipo: Contribuyente - RUC: {}-{}", config.getNumeroDocumento(), config.getDigitoVerificador());
        } else {
            nominacion.setiTipRec(TiNatRec.NO_CONTRIBUYENTE);
            nominacion.setdTipIDRec(config.getTipoDocumentoReceptor());
            nominacion.setdNumID(config.getNumeroDocumento());
            tipoReceptor = "NO_CONTRIBUYENTE";
            documentoReceptor = config.getNumeroDocumento();
            log.info("   Tipo: No Contribuyente - Doc: {} ({})", config.getNumeroDocumento(), config.getTipoDocumentoReceptor());
        }

        TgGroupTiEvt tipoEvento = new TgGroupTiEvt();
        tipoEvento.setrGeVeNotRec(nominacion);

        TrGesEve gestionEvento = new TrGesEve();
        int numeroRandom = new Random().nextInt(99999999) + 1;
        String eventoId = String.valueOf(numeroRandom);
        gestionEvento.setId(eventoId);
        gestionEvento.setdFecFirma(fechaFirma);
        gestionEvento.setgGroupTiEvt(tipoEvento);

        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);

        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);

        EventoNominacionDE eventoNominacion = new EventoNominacionDE();
        eventoNominacion.setDocumentoElectronico(de);
        eventoNominacion.setDocumentoElectronicoId(de.getId());
        eventoNominacion.setSucursal(de.getSucursal()); // Asignar sucursal desde DE
        eventoNominacion.setSucursalId(de.getSucursalId()); // Asignar sucursalId desde DE
        eventoNominacion.setEventoId(eventoId);
        eventoNominacion.setFechaFirma(fechaFirma);
        eventoNominacion.setCdcDocumento(cdc);
        eventoNominacion.setCliente(cliente);
        eventoNominacion.setNombreReceptor(config.getNombreReceptor());
        eventoNominacion.setDocumentoReceptor(documentoReceptor);
        eventoNominacion.setTipoReceptor(tipoReceptor);
        eventoNominacion.setTotalFactura(totalFactura);
        eventoNominacion.setFechaEmision(factura.getFecha());
        eventoNominacion.setFechaRecepcion(fechaRecepcion);
        eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
        eventoNominacion.setActivo(true);

        log.info("   📤 Enviando evento de nominación a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);

        String xmlRespuesta = respuesta.getRespuestaBruta();
        String codigoRespuesta = extraerValorXML(xmlRespuesta, "<dCodRes>", "</dCodRes>");
        if (codigoRespuesta == null) {
            codigoRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dCodRes>", "</ns2:dCodRes>");
        }
        String mensajeRespuesta = extraerValorXML(xmlRespuesta, "<dMsgRes>", "</dMsgRes>");
        if (mensajeRespuesta == null) {
            mensajeRespuesta = extraerValorXML(xmlRespuesta, "<ns2:dMsgRes>", "</ns2:dMsgRes>");
        }
        log.info("   📥 Respuesta recibida - Código: {}", codigoRespuesta);
        log.info("   📥 Mensaje: {}", mensajeRespuesta);

        eventoNominacion.setRespuestaBruta(xmlRespuesta);
        eventoNominacion.setCodigoRespuesta(codigoRespuesta);
        eventoNominacion.setMensajeRespuesta(mensajeRespuesta);

        String estadoResultado = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");
        if (estadoResultado == null) {
            estadoResultado = extraerValorXML(xmlRespuesta, "<ns2:dEstRes>", "</ns2:dEstRes>");
        }
        log.info("   📊 Estado del evento en SIFEN: {}", estadoResultado);

        String protocolo = extraerValorXML(xmlRespuesta, "<dProtAut>", "</dProtAut>");
        if (protocolo == null) {
            protocolo = extraerValorXML(xmlRespuesta, "<ns2:dProtAut>", "</ns2:dProtAut>");
        }
        if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
            eventoNominacion.setProtocoloAutorizacion(protocolo);
            log.info("   📋 Protocolo: {}", protocolo);
        }

        if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
            eventoNominacion.setEstado(EstadoEvento.APROBADO);
            eventoNominacion.setFechaProcesamiento(LocalDateTime.now());
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            factura.setCliente(cliente);
            facturaLegalService.save(factura);
            log.info("   ✅ Evento APROBADO - Factura actualizada con cliente nominado");
            log.info("   📋 Código SIFEN: {} - {}", codigoRespuesta, mensajeRespuesta);
            log.info("   👤 Factura ID {} ahora tiene cliente ID {}", factura.getId(), cliente.getId());
        } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
            eventoNominacion.setEstado(EstadoEvento.RECHAZADO);
            eventoNominacion.setFechaProcesamiento(LocalDateTime.now());
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            log.error("   ❌ Evento RECHAZADO por SIFEN");
            log.error("   📋 Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            log.error("   ℹ️ La factura mantiene cliente NULL (innominada)");
        } else if (estadoResultado == null || estadoResultado.isEmpty()) {
            if ("0300".equals(codigoRespuesta)) {
                eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
                log.info("   ✅ Evento recibido (código 0300) - pendiente de procesamiento");
            } else if ("0600".equals(codigoRespuesta)) {
                if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                    eventoNominacion.setEstado(EstadoEvento.APROBADO);
                    eventoNominacion.setFechaProcesamiento(LocalDateTime.now());
                    factura.setCliente(cliente);
                    facturaLegalService.save(factura);
                    log.info("   ✅ Evento APROBADO (código 0600 + protocolo) - Factura actualizada");
                    log.info("   👤 Factura ID {} ahora tiene cliente ID {}", factura.getId(), cliente.getId());
                } else {
                    eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
                    log.info("   ✅ Evento registrado (código 0600) - estado pendiente");
                }
            } else {
                eventoNominacion.setEstado(EstadoEvento.ERROR_ENVIO);
                log.error("   ❌ Error en envío - Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            }
        } else {
            eventoNominacion.setEstado(EstadoEvento.PENDIENTE);
            log.warn("   ⚠️ Estado desconocido: {} - marcando como PENDIENTE", estadoResultado);
        }

        eventoNominacionDEService.save(eventoNominacion);
        log.info("   💾 Evento guardado en BD - ID: {}, Estado: {}", eventoNominacion.getId(), eventoNominacion.getEstado());
        return respuesta;
    }

    @Transactional
    public void consultarYActualizarEventoCancelacion(EventoCancelacionDE evento) throws SifenException {
        if (evento.getProtocoloAutorizacion() == null || evento.getProtocoloAutorizacion().isEmpty()) {
            log.warn("El evento ID {} no tiene protocolo para ser consultado. Se omite.", evento.getId());
            return;
        }

        DocumentoElectronico de = evento.getDocumentoElectronico();
        if (de == null || de.getCdc() == null) {
            log.warn("El evento ID {} no tiene documento electrónico asociado o CDC. Se omite.", evento.getId());
            return;
        }

        log.info("Consultando estado del DE con CDC {} para verificar evento de cancelación ID {}",
                de.getCdc(), evento.getId());

        // Consultar el estado del documento electrónico
        // Cuando se consulta un DE, SIFEN puede devolver información sobre eventos asociados
        RespuestaConsultaDE respuesta = Sifen.consultaDE(de.getCdc());

        String xmlRespuesta = respuesta.getRespuestaBruta();
        String estadoResultado = extraerValorXML(xmlRespuesta, "<dEstRes>", "</dEstRes>");

        // Verificar si hay información sobre eventos asociados en la respuesta
        // Si el DE está en estado CANCELADO, significa que el evento fue aprobado
        if ("Cancelado".equalsIgnoreCase(extraerValorXML(xmlRespuesta, "<dEst>", "</dEst>"))) {
            evento.setEstado(EstadoEvento.APROBADO);
            evento.setFechaProcesamiento(LocalDateTime.now());

            de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
            documentoElectronicoService.save(de);

            log.info("SCHEDULER: Evento ID {} APROBADO. DE con CDC {} actualizado a CANCELADO.", evento.getId(), de.getCdc());

        } else if ("Aprobado".equalsIgnoreCase(estadoResultado) ||
                   "Rechazado".equalsIgnoreCase(estadoResultado)) {
            // Si hay un estado específico para el evento
            if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
                evento.setEstado(EstadoEvento.APROBADO);
                evento.setFechaProcesamiento(LocalDateTime.now());

                de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
                documentoElectronicoService.save(de);

                log.info("SCHEDULER: Evento ID {} APROBADO. DE con CDC {} actualizado a CANCELADO.", evento.getId(), de.getCdc());
            } else {
                evento.setEstado(EstadoEvento.RECHAZADO);
                evento.setFechaProcesamiento(LocalDateTime.now());
                log.error("SCHEDULER: Evento ID {} RECHAZADO por SIFEN.", evento.getId());
            }
        } else {
            log.info("SCHEDULER: Evento ID {} sigue PENDIENTE. Se reintentará.", evento.getId());
            return;
        }

        evento.setRespuestaBruta(xmlRespuesta);
        eventoCancelacionDEService.save(evento);
    }
}

