package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoCancelacionDE;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoCancelacionDEService;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class SifenEventoService {

    private final DocumentoElectronicoService documentoElectronicoService;
    private final EventoCancelacionDEService eventoCancelacionDEService;
    private final EventoNominacionDEService eventoNominacionDEService;
    private final FacturaLegalService facturaLegalService;

    public SifenEventoService(
            DocumentoElectronicoService documentoElectronicoService,
            EventoCancelacionDEService eventoCancelacionDEService,
            EventoNominacionDEService eventoNominacionDEService,
            FacturaLegalService facturaLegalService) {
        this.documentoElectronicoService = documentoElectronicoService;
        this.eventoCancelacionDEService = eventoCancelacionDEService;
        this.eventoNominacionDEService = eventoNominacionDEService;
        this.facturaLegalService = facturaLegalService;
    }

    @Transactional(noRollbackFor = {SifenException.class, IllegalStateException.class, IllegalArgumentException.class})
    public RespuestaRecepcionEvento cancelarDE(String cdc, String motivo) throws SifenException {
        log.info("🚫 Cancelando DE con CDC: {}", cdc);
        log.info("   Motivo: {}", motivo);

        DocumentoElectronico de = documentoElectronicoService.findByCdc(cdc)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró DE con CDC: " + cdc));

        if (eventoCancelacionDEService.tieneCancelacionAprobada(de.getId(), de.getSucursalId())) {
            log.warn("⚠️ El DE ya tiene un evento de cancelación APROBADO");
            log.warn("   Estado del DE: {}", de.getEstado());
            throw new IllegalStateException("El DE ya fue cancelado exitosamente. No se puede cancelar nuevamente.");
        }

        List<EventoCancelacionDE> eventosActivos = eventoCancelacionDEService.findActivosByCdcDocumento(cdc);
        if (!eventosActivos.isEmpty()) {
            log.info("   🔄 Se encontraron {} evento(s) previo(s) - realizando reintento automático", eventosActivos.size());
            for (EventoCancelacionDE eventoAnterior : eventosActivos) {
                eventoAnterior.setActivo(false);
                eventoCancelacionDEService.save(eventoAnterior);
                log.info("      📝 Evento anterior ID {} marcado como inactivo (estado: {})", eventoAnterior.getId(), eventoAnterior.getEstado());
            }
        }

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

        EventoCancelacionDE eventoCancelacion = new EventoCancelacionDE();
        eventoCancelacion.setDocumentoElectronico(de);
        eventoCancelacion.setDocumentoElectronicoId(de.getId());
        eventoCancelacion.setSucursal(de.getSucursal()); // Asignar sucursal desde DE
        eventoCancelacion.setSucursalId(de.getSucursalId()); // Asignar sucursalId desde DE
        eventoCancelacion.setEventoId(eventoId);
        eventoCancelacion.setFechaFirma(fechaFirma);
        eventoCancelacion.setCdcDocumento(cdc);
        eventoCancelacion.setMotivoCancelacion(motivo);
        eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
        eventoCancelacion.setActivo(true);

        log.info("   📤 Enviando evento de cancelación a SIFEN...");
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

        // Variable para controlar si debemos lanzar excepción
        String errorMessage = null;

        if ("Aprobado".equalsIgnoreCase(estadoResultado)) {
            eventoCancelacion.setEstado(EstadoEvento.APROBADO);
            eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
            de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            log.info("   ✅ Evento APROBADO - DE actualizado a estado CANCELADO");
            log.info("   📋 Código SIFEN: {} - {}", codigoRespuesta, mensajeRespuesta);
        } else if ("Rechazado".equalsIgnoreCase(estadoResultado)) {
            eventoCancelacion.setEstado(EstadoEvento.RECHAZADO);
            eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
            de.setCodigoRespuestaSifen(codigoRespuesta);
            de.setMensajeRespuestaSifen(mensajeRespuesta);
            documentoElectronicoService.save(de);
            log.error("   ❌ Evento RECHAZADO por SIFEN");
            log.error("   📋 Código: {} - {}", codigoRespuesta, mensajeRespuesta);
            log.error("   ℹ️ El DE mantiene su estado actual: {}", de.getEstado());
            errorMessage = "SIFEN rechazó la cancelación: " + mensajeRespuesta;
        } else if (estadoResultado == null || estadoResultado.isEmpty()) {
            if ("0300".equals(codigoRespuesta)) {
                eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
                log.info("   ✅ Evento recibido (código 0300) - pendiente de procesamiento");
            } else if ("0600".equals(codigoRespuesta)) {
                if (protocolo != null && !protocolo.isEmpty() && !"0".equals(protocolo)) {
                    eventoCancelacion.setEstado(EstadoEvento.APROBADO);
                    eventoCancelacion.setFechaProcesamiento(LocalDateTime.now());
                    de.setEstado(com.franco.dev.domain.financiero.enums.EstadoDE.CANCELADO);
                    documentoElectronicoService.save(de);
                    log.info("   ✅ Evento APROBADO (código 0600 + protocolo) - DE actualizado a CANCELADO");
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
            eventoCancelacion.setEstado(EstadoEvento.PENDIENTE);
            log.warn("   ⚠️ Estado desconocido: {} - marcando como PENDIENTE", estadoResultado);
        }

        // Guardar el evento UNA sola vez al final
        eventoCancelacionDEService.save(eventoCancelacion);
        log.info("   💾 Evento guardado en BD - ID: {}, Estado: {}", eventoCancelacion.getId(), eventoCancelacion.getEstado());

        // Si hubo error, lanzar excepción DESPUÉS de guardar
        if (errorMessage != null) {
            throw new IllegalStateException(errorMessage);
        }

        return respuesta;
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

    @Transactional
    public RespuestaRecepcionEvento inutilizarNumeros(
            Timbrado timbrado,
            String establecimiento,
            String puntoExpedicion,
            int numeroInicio,
            int numeroFin,
            TTiDE tipoDE,
            String motivo) throws SifenException {

        log.info("📝 Inutilizando números de documentos");
        log.info("   Timbrado: {}", timbrado.getNumero());
        log.info("   Establecimiento: {}", establecimiento);
        log.info("   Punto Expedición: {}", puntoExpedicion);
        log.info("   Rango: {} - {}", numeroInicio, numeroFin);
        log.info("   Tipo DE: {}", tipoDE);
        log.info("   Motivo: {}", motivo);

        if (numeroInicio > numeroFin) {
            throw new IllegalArgumentException("Número inicial (" + numeroInicio + ") no puede ser mayor que número final (" + numeroFin + ")");
        }

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

        String eventoId = String.format("INU-%s-%s-%s-%07d", timbrado.getNumero(), establecimiento, puntoExpedicion, numeroInicio);
        TrGesEve gestionEvento = new TrGesEve();
        gestionEvento.setId(eventoId);
        gestionEvento.setdFecFirma(LocalDateTime.now());
        gestionEvento.setgGroupTiEvt(tipoEvento);

        List<TrGesEve> listaEventos = new ArrayList<>();
        listaEventos.add(gestionEvento);

        EventosDE eventosDE = new EventosDE();
        eventosDE.setrGesEveList(listaEventos);

        log.info("   📤 Enviando evento de inutilización a SIFEN...");
        RespuestaRecepcionEvento respuesta = Sifen.recepcionEvento(eventosDE);

        log.info("   📥 Respuesta recibida - Código: {}", respuesta.getdCodRes());
        log.info("✅ Evento de inutilización procesado");

        return respuesta;
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

