package com.franco.dev.service.operaciones;

import com.franco.dev.service.ia.FacturaIaResponse;
import com.franco.dev.service.sifen.util.SifenXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de XML SIFEN (Documento Electronico DE) para extraer datos al mismo formato
 * que el pipeline de IA. Reusa SifenXmlParser.extractTagValue() para tags simples.
 *
 * Tags relevantes del DE (siRecepDE_v150.xsd):
 *   - dRucEm + dDVEmi   -> emisorRuc "XXXXXXXX-X"
 *   - dNomEmi           -> emisorNombre
 *   - dEst, dPunExp, dNumDoc -> numeroFactura "EST-PUN-NUM"
 *   - dNumTim           -> timbrado
 *   - dFeEmiDE          -> fechaEmision (yyyy-MM-dd extraido de iso 8601)
 *   - cMoneOpe          -> moneda
 *   - dTotGralOpe       -> totalGeneral
 *   - gCamItem (multiple) -> items
 *     - dCodInt, dDesProSer, dCantProSer
 *     - dPUniProSer, dDescItem, dTotOpeItem
 *
 * esLegal=true siempre que el XML sea un DE valido (tiene <rDE> + <dRucEm>).
 */
@Service
public class FacturaSifenXmlParserService {

    private static final Logger log = LoggerFactory.getLogger(FacturaSifenXmlParserService.class);

    /**
     * @param xmlContent contenido XML completo del DE SIFEN
     * @return FacturaIaResponse poblado. Si no es DE valido, retorna response con error.
     */
    public FacturaIaResponse parsearXml(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            FacturaIaResponse r = new FacturaIaResponse();
            r.setError("XML_VACIO");
            return r;
        }

        // Validacion minima: debe ser un DE SIFEN (rDE + dRucEm).
        // Usar contains de prefijo "<rDE" porque el tag real viene con atributos xmlns/xsi:schemaLocation,
        // no como "<rDE>" literal. dRucEm normalmente no tiene atributos.
        if (!contieneTagConPosiblesAtributos(xmlContent, "rDE") ||
                !SifenXmlParser.hasTag(xmlContent, "dRucEm")) {
            FacturaIaResponse r = new FacturaIaResponse();
            r.setError("NO_ES_DE_SIFEN");
            return r;
        }

        FacturaIaResponse resp = new FacturaIaResponse();
        resp.setEsLegal(Boolean.TRUE); // todo DE SIFEN es factura legal

        String ruc = SifenXmlParser.extractTagValue(xmlContent, "dRucEm");
        String dv = SifenXmlParser.extractTagValue(xmlContent, "dDVEmi");
        if (ruc != null) {
            resp.setEmisorRuc(dv != null ? ruc + "-" + dv : ruc);
        }
        resp.setEmisorNombre(SifenXmlParser.extractTagValue(xmlContent, "dNomEmi"));

        String est = SifenXmlParser.extractTagValue(xmlContent, "dEst");
        String pun = SifenXmlParser.extractTagValue(xmlContent, "dPunExp");
        String num = SifenXmlParser.extractTagValue(xmlContent, "dNumDoc");
        if (est != null && pun != null && num != null) {
            resp.setNumeroFactura(est + "-" + pun + "-" + num);
        }

        resp.setTimbrado(SifenXmlParser.extractTagValue(xmlContent, "dNumTim"));

        String fechaIso = SifenXmlParser.extractTagValue(xmlContent, "dFeEmiDE");
        if (fechaIso != null && fechaIso.length() >= 10) {
            // dFeEmiDE viene como "2025-12-04T18:20:33" — tomar solo fecha
            resp.setFechaEmision(fechaIso.substring(0, 10));
        }

        resp.setMoneda(SifenXmlParser.extractTagValue(xmlContent, "cMoneOpe"));

        String totalStr = SifenXmlParser.extractTagValue(xmlContent, "dTotGralOpe");
        if (totalStr != null) {
            try {
                resp.setTotalGeneral(new BigDecimal(totalStr.trim()));
            } catch (NumberFormatException e) {
                log.warn("dTotGralOpe no parseable: {}", totalStr);
            }
        }

        resp.setItems(parsearItems(xmlContent));

        log.info("XML SIFEN parseado: ruc={} numero={} items={}",
                resp.getEmisorRuc(), resp.getNumeroFactura(),
                resp.getItems() != null ? resp.getItems().size() : 0);

        return resp;
    }

    private List<FacturaIaResponse.Item> parsearItems(String xmlContent) {
        List<FacturaIaResponse.Item> items = new ArrayList<>();
        List<String> bloques = extractAllFullTags(xmlContent, "gCamItem");
        for (String bloque : bloques) {
            FacturaIaResponse.Item item = new FacturaIaResponse.Item();
            item.setCodigoProducto(SifenXmlParser.extractTagValue(bloque, "dCodInt"));
            // dGtin = codigo de barras EAN (el que matchea nuestro catalogo; dCodInt es el codigo interno del
            // proveedor). Es opcional: no todos los items lo traen -> silenciar el warning.
            item.setCodigoBarras(SifenXmlParser.extractTagValue(bloque, "dGtin", false));
            item.setNombreProducto(SifenXmlParser.extractTagValue(bloque, "dDesProSer"));
            item.setCantidad(parseBigDecimalSafe(SifenXmlParser.extractTagValue(bloque, "dCantProSer")));
            item.setPrecioUnitario(parseBigDecimalSafe(SifenXmlParser.extractTagValue(bloque, "dPUniProSer")));
            item.setDescuento(parseBigDecimalSafe(SifenXmlParser.extractTagValue(bloque, "dDescItem")));
            item.setTotalItem(parseBigDecimalSafe(SifenXmlParser.extractTagValue(bloque, "dTotOpeItem")));
            items.add(item);
        }
        return items;
    }

    /**
     * Extrae todas las ocurrencias completas de un tag (incluyendo open/close).
     * SifenXmlParser.extractFullTag solo retorna la primera; este helper itera.
     */
    List<String> extractAllFullTags(String xml, String tagName) {
        List<String> result = new ArrayList<>();
        if (xml == null || tagName == null) return result;
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int cursor = 0;
        while (cursor < xml.length()) {
            int start = xml.indexOf(openTag, cursor);
            if (start < 0) break;
            int end = xml.indexOf(closeTag, start);
            if (end < 0) break;
            int endComplete = end + closeTag.length();
            result.add(xml.substring(start, endComplete));
            cursor = endComplete;
        }
        return result;
    }

    /**
     * Check si el XML contiene `<tagName>` o `<tagName ...>` o `<tagName/>`. Tolerante a atributos.
     */
    private boolean contieneTagConPosiblesAtributos(String xml, String tagName) {
        if (xml == null) return false;
        String prefix = "<" + tagName;
        int idx = xml.indexOf(prefix);
        while (idx >= 0) {
            int after = idx + prefix.length();
            if (after < xml.length()) {
                char c = xml.charAt(after);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
                    return true;
                }
            }
            idx = xml.indexOf(prefix, idx + 1);
        }
        return false;
    }

    private BigDecimal parseBigDecimalSafe(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            log.debug("BigDecimal no parseable: {}", s);
            return null;
        }
    }
}
