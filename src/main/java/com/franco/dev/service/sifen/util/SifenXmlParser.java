package com.franco.dev.service.sifen.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase de utilidad para extraer información de XMLs de SIFEN
 * sin necesidad de un parseador XML completo.
 */
public class SifenXmlParser {
    
    private static final Logger logger = LoggerFactory.getLogger(SifenXmlParser.class);

    /**
     * Extrae el valor de un tag XML simple
     * @param xml El XML completo
     * @param tagName El nombre del tag a buscar (sin < >)
     * @return El valor del tag o null si no se encuentra
     */
    public static String extractTagValue(String xml, String tagName) {
        if (xml == null || tagName == null) {
            return null;
        }
        
        try {
            String openTag = "<" + tagName + ">";
            String closeTag = "</" + tagName + ">";
            
            int startIndex = xml.indexOf(openTag);
            if (startIndex == -1) {
                // Intentar con namespace (ej: <ns:tag>)
                startIndex = xml.indexOf(":" + tagName + ">");
                if (startIndex == -1) {
                    return null;
                }
                // Retroceder para encontrar el inicio del tag
                int tagStart = xml.lastIndexOf("<", startIndex);
                if (tagStart == -1) {
                    return null;
                }
                openTag = xml.substring(tagStart, startIndex + tagName.length() + 2);
                startIndex = tagStart;
            }
            
            startIndex += openTag.length();
            int endIndex = xml.indexOf(closeTag, startIndex);
            
            if (endIndex == -1) {
                return null;
            }
            
            return xml.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            logger.error("Error al extraer tag '{}' del XML: {}", tagName, e.getMessage());
            return null;
        }
    }

    /**
     * Extrae la URL del QR (dCarQR) del XML
     */
    public static String extractUrlQr(String xml) {
        return extractTagValue(xml, "dCarQR");
    }

    /**
     * Extrae el CDC (Id) del XML firmado
     */
    public static String extractCdc(String xml) {
        String cdc = extractTagValue(xml, "Id");
        if (cdc == null) {
            // Intentar extraer del atributo Id en el tag rDE
            try {
                String searchPattern = "Id=\"";
                int startIndex = xml.indexOf(searchPattern);
                if (startIndex != -1) {
                    startIndex += searchPattern.length();
                    int endIndex = xml.indexOf("\"", startIndex);
                    if (endIndex != -1) {
                        cdc = xml.substring(startIndex, endIndex);
                    }
                }
            } catch (Exception e) {
                logger.error("Error al extraer CDC del XML: {}", e.getMessage());
            }
        }
        return cdc;
    }

    /**
     * Extrae el DigestValue del XML firmado
     */
    public static String extractDigestValue(String xml) {
        return extractTagValue(xml, "DigestValue");
    }

    /**
     * Extrae el código de estado de la respuesta de SIFEN
     */
    public static String extractCodigoEstado(String xml) {
        String codigo = extractTagValue(xml, "dCodRes");
        if (codigo == null) {
            codigo = extractTagValue(xml, "gResProc");
        }
        return codigo;
    }

    /**
     * Extrae el mensaje de estado de la respuesta de SIFEN
     */
    public static String extractMensajeEstado(String xml) {
        String mensaje = extractTagValue(xml, "dMsgRes");
        if (mensaje == null) {
            mensaje = extractTagValue(xml, "dMsg");
        }
        return mensaje;
    }

    /**
     * Extrae el número de protocolo de una respuesta de envío de lote
     */
    public static String extractProtocolo(String xml) {
        return extractTagValue(xml, "dProtConsLote");
    }

    /**
     * Verifica si el XML contiene un error
     */
    public static boolean contieneError(String xml) {
        if (xml == null) {
            return true;
        }
        String codigo = extractCodigoEstado(xml);
        // Códigos que comienzan con 0 o 1 son errores, 0300 es éxito
        if (codigo != null) {
            return !codigo.equals("0300") && !codigo.equals("0600");
        }
        return xml.toLowerCase().contains("error") || xml.toLowerCase().contains("fault");
    }

    /**
     * Extrae el código de respuesta de procesamiento
     */
    public static String extractCodigoRespuesta(String xml) {
        return extractTagValue(xml, "dCodRes");
    }

    /**
     * Extrae el mensaje de respuesta de procesamiento
     */
    public static String extractMensajeRespuesta(String xml) {
        return extractTagValue(xml, "dMsgRes");
    }

    /**
     * Extrae el estado de un DE individual de una respuesta de lote
     */
    public static String extractEstadoDE(String xmlDE) {
        String estado = extractTagValue(xmlDE, "dEstRes");
        return estado;
    }

    /**
     * Extrae todos los bloques de DE de una respuesta de lote
     * Retorna el XML completo de cada DE
     */
    public static java.util.List<String> extractBloquesDEs(String xml) {
        java.util.List<String> bloques = new java.util.ArrayList<>();
        if (xml == null) {
            return bloques;
        }
        
        try {
            String startTag = "<rRetEnvi>";
            String endTag = "</rRetEnvi>";
            
            int currentIndex = 0;
            while (true) {
                int startIndex = xml.indexOf(startTag, currentIndex);
                if (startIndex == -1) {
                    break;
                }
                
                int endIndex = xml.indexOf(endTag, startIndex);
                if (endIndex == -1) {
                    break;
                }
                
                endIndex += endTag.length();
                String bloque = xml.substring(startIndex, endIndex);
                bloques.add(bloque);
                
                currentIndex = endIndex;
            }
        } catch (Exception e) {
            logger.error("Error al extraer bloques de DEs: {}", e.getMessage());
        }
        
        return bloques;
    }
}

