package com.franco.dev.service.sifen.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser especializado para extraer eventos asociados de las respuestas de consulta de DE.
 * SIFEN puede devolver información sobre eventos de cancelación o nominación
 * cuando se consulta un DE.
 */
@Slf4j
public class SifenEventoParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Extrae eventos de cancelación del XML de respuesta de consulta DE
     */
    public static List<EventoCancelacion> extraerEventosCancelacion(String xmlRespuesta) {
        List<EventoCancelacion> eventos = new ArrayList<>();
        
        if (xmlRespuesta == null || !xmlRespuesta.contains("rGeVeCan")) {
            return eventos;
        }

        try {
            int currentIndex = 0;
            while (true) {
                int startIndex = xmlRespuesta.indexOf("<rGeVeCan>", currentIndex);
                if (startIndex == -1) break;
                
                int endIndex = xmlRespuesta.indexOf("</rGeVeCan>", startIndex);
                if (endIndex == -1) break;
                
                String bloqueEvento = xmlRespuesta.substring(startIndex, endIndex + "</rGeVeCan>".length());
                EventoCancelacion evento = parsearEventoCancelacion(bloqueEvento);
                if (evento != null) {
                    eventos.add(evento);
                }
                
                currentIndex = endIndex;
            }
        } catch (Exception e) {
            log.error("Error al extraer eventos de cancelación: {}", e.getMessage(), e);
        }
        
        return eventos;
    }

    /**
     * Extrae eventos de nominación del XML de respuesta de consulta DE
     */
    public static List<EventoNominacion> extraerEventosNominacion(String xmlRespuesta) {
        List<EventoNominacion> eventos = new ArrayList<>();
        
        if (xmlRespuesta == null || !xmlRespuesta.contains("rGeVeNotRec")) {
            return eventos;
        }

        try {
            int currentIndex = 0;
            while (true) {
                int startIndex = xmlRespuesta.indexOf("<rGeVeNotRec>", currentIndex);
                if (startIndex == -1) break;
                
                int endIndex = xmlRespuesta.indexOf("</rGeVeNotRec>", startIndex);
                if (endIndex == -1) break;
                
                String bloqueEvento = xmlRespuesta.substring(startIndex, endIndex + "</rGeVeNotRec>".length());
                EventoNominacion evento = parsearEventoNominacion(bloqueEvento);
                if (evento != null) {
                    eventos.add(evento);
                }
                
                currentIndex = endIndex;
            }
        } catch (Exception e) {
            log.error("Error al extraer eventos de nominación: {}", e.getMessage(), e);
        }
        
        return eventos;
    }

    private static EventoCancelacion parsearEventoCancelacion(String bloqueXml) {
        try {
            EventoCancelacion evento = new EventoCancelacion();
            
            evento.setEventoId(SifenXmlParser.extractTagValue(bloqueXml, "Id"));
            evento.setCdcDocumento(SifenXmlParser.extractTagValue(bloqueXml, "mOtEve"));
            evento.setMotivoCancelacion(SifenXmlParser.extractTagValue(bloqueXml, "mOtEve"));
            
            String fechaStr = SifenXmlParser.extractTagValue(bloqueXml, "dFecFirma");
            if (fechaStr != null) {
                try {
                    evento.setFechaFirma(LocalDateTime.parse(fechaStr, DATE_FORMATTER));
                } catch (Exception e) {
                    log.warn("Error al parsear fecha de firma: {}", fechaStr);
                }
            }
            
            evento.setEstado(SifenXmlParser.extractTagValue(bloqueXml, "dEstRes"));
            evento.setCodigoRespuesta(SifenXmlParser.extractTagValue(bloqueXml, "dCodRes"));
            evento.setMensajeRespuesta(SifenXmlParser.extractTagValue(bloqueXml, "dMsgRes"));
            
            return evento;
        } catch (Exception e) {
            log.error("Error al parsear evento de cancelación: {}", e.getMessage());
            return null;
        }
    }

    private static EventoNominacion parsearEventoNominacion(String bloqueXml) {
        try {
            EventoNominacion evento = new EventoNominacion();
            
            evento.setEventoId(SifenXmlParser.extractTagValue(bloqueXml, "Id"));
            evento.setCdcDocumento(SifenXmlParser.extractTagValue(bloqueXml, "Id")); // El CDC del documento original
            evento.setNombreReceptor(SifenXmlParser.extractTagValue(bloqueXml, "dNomRec"));
            evento.setDocumentoReceptor(SifenXmlParser.extractTagValue(bloqueXml, "dNumIDRec"));
            evento.setTipoReceptor(SifenXmlParser.extractTagValue(bloqueXml, "iTipIDRec"));
            
            String fechaStr = SifenXmlParser.extractTagValue(bloqueXml, "dFecFirma");
            if (fechaStr != null) {
                try {
                    evento.setFechaFirma(LocalDateTime.parse(fechaStr, DATE_FORMATTER));
                } catch (Exception e) {
                    log.warn("Error al parsear fecha de firma: {}", fechaStr);
                }
            }
            
            evento.setEstado(SifenXmlParser.extractTagValue(bloqueXml, "dEstRes"));
            evento.setCodigoRespuesta(SifenXmlParser.extractTagValue(bloqueXml, "dCodRes"));
            evento.setMensajeRespuesta(SifenXmlParser.extractTagValue(bloqueXml, "dMsgRes"));
            
            return evento;
        } catch (Exception e) {
            log.error("Error al parsear evento de nominación: {}", e.getMessage());
            return null;
        }
    }

    /**
     * DTO para eventos de cancelación
     */
    @Data
    public static class EventoCancelacion {
        private String eventoId;
        private String cdcDocumento;
        private String motivoCancelacion;
        private LocalDateTime fechaFirma;
        private String estado;
        private String codigoRespuesta;
        private String mensajeRespuesta;
    }

    /**
     * DTO para eventos de nominación
     */
    @Data
    public static class EventoNominacion {
        private String eventoId;
        private String cdcDocumento;
        private String nombreReceptor;
        private String documentoReceptor;
        private String tipoReceptor;
        private LocalDateTime fechaFirma;
        private String estado;
        private String codigoRespuesta;
        private String mensajeRespuesta;
    }
}

