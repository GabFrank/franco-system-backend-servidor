package com.franco.dev.service.sifen.util;

import com.roshka.sifen.core.fields.request.de.TgDatRec;
import com.roshka.sifen.core.types.PaisType;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipDocRec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Receptor de una nota de credito sobre una factura B2F (#666): el mismo gDatRec que SIFEN aprobo en la
 * factura. El snapshot de la nota (nombre, ruc, direccion) no trae el pais ni el tipo de documento.
 */
public final class SifenReceptorDesdeXml {

    private SifenReceptorDesdeXml() {
    }

    /** gDatRec de la factura si es B2F (iTiOpe=4); null si no lo es o no se puede leer. */
    public static TgDatRec receptorB2F(String xmlFactura) {
        if (xmlFactura == null || xmlFactura.isEmpty()) return null;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xmlFactura.getBytes(StandardCharsets.UTF_8)));
            NodeList l = doc.getElementsByTagName("gDatRec");
            if (l.getLength() == 0) return null;
            Element g = (Element) l.item(0);
            if (!"4".equals(texto(g, "iTiOpe"))) return null;

            TgDatRec r = new TgDatRec();
            r.setiNatRec(TiNatRec.getByVal(Short.parseShort(texto(g, "iNatRec"))));
            r.setiTiOpe(TiTiOpe.B2F);
            r.setcPaisRec(PaisType.valueOf(texto(g, "cPaisRec")));
            r.setdNomRec(texto(g, "dNomRec"));
            r.setdDirRec(texto(g, "dDirRec"));
            String casa = texto(g, "dNumCasRec");
            if (casa != null) r.setdNumCasRec(Integer.parseInt(casa));
            String tipo = texto(g, "iTipIDRec");
            if (tipo != null) r.setiTipIDRec(TiTipDocRec.getByVal(Short.parseShort(tipo)));
            r.setdNumIDRec(texto(g, "dNumIDRec"));
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String texto(Element padre, String tag) {
        NodeList l = padre.getElementsByTagName(tag);
        return l.getLength() == 0 ? null : l.item(0).getTextContent().trim();
    }
}
