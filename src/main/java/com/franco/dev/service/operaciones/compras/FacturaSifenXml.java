package com.franco.dev.service.operaciones.compras;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee la factura que devuelve SIFEN en la consulta por CDC (siConsDE): el {@code rDE} dentro de
 * {@code xContenDE}.
 *
 * ⚠️ Parser propio a propósito. jsifenlib tipa {@code dGtin} como {@code long} (pierde los ceros a la
 * izquierda de un código de barras) y sus clases están pensadas para emitir, no para leer lo que emitió otro.
 *
 * No depende de prefijos de namespace: busca por nombre local. El {@code rDE} puede venir como elementos o como
 * texto escapado dentro de {@code xContenDE}; se aceptan las dos formas.
 */
public final class FacturaSifenXml {

    private FacturaSifenXml() {
    }

    /** Cabecera de la factura. Montos tal como los manda SIFEN (IVA incluido). */
    public static class Cabecera {
        public String cdc;
        public Integer tipoDocumento;
        public Integer timbrado;
        public String establecimiento;
        public String puntoExpedicion;
        public String numero;
        public LocalDateTime fechaEmision;
        public String rucEmisor;
        public String dvEmisor;
        public String razonSocialEmisor;
        public String rucReceptor;
        public String dvReceptor;
        public String razonSocialReceptor;
        public String moneda;
        public Double cotizacion;
        public Integer condicionOperacion;
        /** iTiPago del primer pago al contado (1 efectivo, 2 cheque, 3/4 tarjeta, 5 transferencia…). Null si no viene. */
        public Integer tipoPago;
        /** dDesTiPag: «Cheque», «Efectivo»… tal como lo escribió el proveedor. */
        public String tipoPagoDescripcion;
        /** Días de plazo de una venta a crédito (dPlazoCre «30 días» → 30). Null si no viene. */
        public Integer plazoCredito;
        public Double subtotalExenta;
        public Double subtotalIva5;
        public Double subtotalIva10;
        public Double totalDescuento;
        public Double totalGeneral;
        public Double totalIva5;
        public Double totalIva10;
        public Double totalIva;
        /** Hay un evento de cancelación en la respuesta. */
        public boolean cancelada;
        // Para el KuDE: lo que el emisor imprime en su factura
        public String tipoDocumentoDescripcion;
        public LocalDate inicioTimbrado;
        public String direccionEmisor;
        public String ciudadEmisor;
        public String departamentoEmisor;
        public String telefonoEmisor;
        public String emailEmisor;
        public String actividadEconomica;
        public String direccionReceptor;
        public String telefonoReceptor;
        public String emailReceptor;
        /** dCarQR: la URL del QR que se imprime en el KuDE. */
        public String urlQr;
        public final List<Renglon> renglones = new ArrayList<>();
    }

    public static class Renglon {
        public int orden;
        public String codigoProveedor;
        public String gtin;
        public String gtinPaquete;
        /** dInfItem: texto libre del emisor por renglón. Algunos (GENFI) ponen ahí el código de barras. */
        public String informacion;
        public String descripcion;
        public String unidadMedida;
        public Double cantidad;
        public Double precioUnitario;
        public Double descuentoUnitario;
        public Double porcentajeDescuento;
        public Double total;
        public Integer afectacionIva;
        public Integer tasaIva;
        public String lote;
        public LocalDate vencimiento;
    }

    /** @throws IllegalArgumentException si la respuesta no trae una factura legible. */
    public static Cabecera leer(String respuestaSifen) {
        if (respuestaSifen == null || respuestaSifen.trim().isEmpty()) {
            throw new IllegalArgumentException("SIFEN no devolvió el contenido de la factura.");
        }
        Element rde = buscarRde(parsear(respuestaSifen));
        if (rde == null) {
            throw new IllegalArgumentException("La respuesta de SIFEN no trae la factura (rDE).");
        }
        Cabecera c = new Cabecera();
        Element de = primero(rde, "DE");
        if (de == null) {
            throw new IllegalArgumentException("La factura de SIFEN no tiene el grupo DE.");
        }
        c.cdc = de.getAttribute("Id");

        Element gTimb = primero(de, "gTimb");
        c.tipoDocumento = entero(texto(gTimb, "iTiDE"));
        c.timbrado = entero(texto(gTimb, "dNumTim"));
        c.establecimiento = texto(gTimb, "dEst");
        c.puntoExpedicion = texto(gTimb, "dPunExp");
        c.numero = texto(gTimb, "dNumDoc");
        c.tipoDocumentoDescripcion = texto(gTimb, "dDesTiDE");
        c.inicioTimbrado = fecha(texto(gTimb, "dFeIniT"));

        Element gral = primero(de, "gDatGralOpe");
        c.fechaEmision = fechaHora(texto(gral, "dFeEmiDE"));
        Element gOpeCom = primero(gral, "gOpeCom");
        c.moneda = texto(gOpeCom, "cMoneOpe");
        c.cotizacion = numero(texto(gOpeCom, "dTiCam"));
        Element gEmis = primero(gral, "gEmis");
        c.rucEmisor = sinCeros(texto(gEmis, "dRucEm"));
        c.dvEmisor = texto(gEmis, "dDVEmi");
        c.razonSocialEmisor = texto(gEmis, "dNomEmi");
        String calle = texto(gEmis, "dDirEmi");
        String casa = texto(gEmis, "dNumCas");
        c.direccionEmisor = calle == null ? null : calle + (casa != null && !"0".equals(casa.trim()) ? " " + casa : "");
        c.ciudadEmisor = texto(gEmis, "dDesCiuEmi");
        c.departamentoEmisor = texto(gEmis, "dDesDepEmi");
        c.telefonoEmisor = texto(gEmis, "dTelEmi");
        c.emailEmisor = texto(gEmis, "dEmailE");
        c.actividadEconomica = texto(primero(gEmis, "gActEco"), "dDesActEco");
        Element gDatRec = primero(gral, "gDatRec");
        c.rucReceptor = sinCeros(texto(gDatRec, "dRucRec"));
        c.dvReceptor = texto(gDatRec, "dDVRec");
        c.razonSocialReceptor = texto(gDatRec, "dNomRec");
        c.direccionReceptor = texto(gDatRec, "dDirRec");
        c.telefonoReceptor = texto(gDatRec, "dTelRec");
        c.emailReceptor = texto(gDatRec, "dEmailRec");
        c.urlQr = texto(primero(rde, "gCamFuFD"), "dCarQR");

        Element gDtipDE = primero(de, "gDtipDE");
        Element gCamCond = primero(gDtipDE, "gCamCond");
        c.condicionOperacion = entero(texto(gCamCond, "iCondOpe"));
        Element pago = primero(gCamCond, "gPaConEIni");
        c.tipoPago = entero(texto(pago, "iTiPago"));
        c.tipoPagoDescripcion = texto(pago, "dDesTiPag");
        c.plazoCredito = dias(texto(primero(gCamCond, "gPagCred"), "dPlazoCre"));
        int orden = 1;
        for (Element item : hijos(gDtipDE, "gCamItem")) {
            c.renglones.add(renglon(item, orden++));
        }

        Element tot = primero(de, "gTotSub");
        c.subtotalExenta = numero(texto(tot, "dSubExe"));
        c.subtotalIva5 = numero(texto(tot, "dSub5"));
        c.subtotalIva10 = numero(texto(tot, "dSub10"));
        c.totalDescuento = numero(texto(tot, "dTotDesc"));
        c.totalGeneral = numero(texto(tot, "dTotGralOpe"));
        c.totalIva5 = numero(texto(tot, "dIVA5"));
        c.totalIva10 = numero(texto(tot, "dIVA10"));
        c.totalIva = numero(texto(tot, "dTotIVA"));

        // Un evento de cancelación aprobado viaja en la misma respuesta (rGeVeCan dentro de xContEv).
        c.cancelada = respuestaSifen.contains("rGeVeCan");
        return c;
    }

    private static Renglon renglon(Element item, int orden) {
        Renglon r = new Renglon();
        r.orden = orden;
        r.codigoProveedor = texto(item, "dCodInt");
        r.gtin = texto(item, "dGtin");
        r.gtinPaquete = texto(item, "dGtinPq");
        r.descripcion = texto(item, "dDesProSer");
        r.informacion = texto(item, "dInfItem");
        if (r.gtin == null) {
            // Sin dGtin, el código de barras suele venir escrito en dInfItem («7790580716707 - UME: C») o en la
            // descripción: es lo que el KuDE imprime debajo del producto
            r.gtin = codigoDeBarrasEn(r.informacion);
            if (r.gtin == null) r.gtin = codigoDeBarrasEn(r.descripcion);
        }
        String unidad = texto(item, "dDesUniMed");
        r.unidadMedida = unidad != null ? unidad : texto(item, "cUniMed");
        r.cantidad = numero(texto(item, "dCantProSer"));
        Element valor = primero(item, "gValorItem");
        r.precioUnitario = numero(texto(valor, "dPUniProSer"));
        Element resta = primero(valor, "gValorRestaItem");
        r.descuentoUnitario = numero(texto(resta, "dDescItem"));
        r.porcentajeDescuento = numero(texto(resta, "dPorcDesIt"));
        r.total = numero(texto(resta, "dTotOpeItem"));
        Element iva = primero(item, "gCamIVA");
        r.afectacionIva = entero(texto(iva, "iAfecIVA"));
        r.tasaIva = entero(texto(iva, "dTasaIVA"));
        Element merc = primero(item, "gRasMerc");
        r.lote = texto(merc, "dNumLote");
        r.vencimiento = fecha(texto(merc, "dVencMerc"));
        return r;
    }

    // ----------------------------------------------------------------------------------- DOM

    private static Document parsear(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // XXE: la respuesta viene de afuera
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new InputSource(new StringReader(xml.trim())));
        } catch (Exception e) {
            throw new IllegalArgumentException("La respuesta de SIFEN no es un XML legible: " + e.getMessage(), e);
        }
    }

    /** El rDE como elemento, o el texto escapado de xContenDE vuelto a parsear. */
    private static Element buscarRde(Document doc) {
        NodeList rdes = doc.getElementsByTagNameNS("*", "rDE");
        if (rdes.getLength() > 0) {
            return (Element) rdes.item(0);
        }
        NodeList contenedores = doc.getElementsByTagNameNS("*", "xContenDE");
        for (int i = 0; i < contenedores.getLength(); i++) {
            String interno = contenedores.item(i).getTextContent();
            if (interno != null && interno.contains("rDE")) {
                int inicio = interno.indexOf('<');
                if (inicio < 0) continue;
                NodeList internos = parsear(interno.substring(inicio)).getElementsByTagNameNS("*", "rDE");
                if (internos.getLength() > 0) {
                    return (Element) internos.item(0);
                }
            }
        }
        return null;
    }

    private static Element primero(Element padre, String nombre) {
        if (padre == null) return null;
        NodeList lista = padre.getElementsByTagNameNS("*", nombre);
        return lista.getLength() > 0 ? (Element) lista.item(0) : null;
    }

    private static List<Element> hijos(Element padre, String nombre) {
        List<Element> lista = new ArrayList<>();
        if (padre == null) return lista;
        NodeList nodos = padre.getChildNodes();
        for (int i = 0; i < nodos.getLength(); i++) {
            Node n = nodos.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && nombre.equals(n.getLocalName())) {
                lista.add((Element) n);
            }
        }
        return lista;
    }

    private static String texto(Element padre, String nombre) {
        Element e = primero(padre, nombre);
        if (e == null) return null;
        String t = e.getTextContent();
        return t == null || t.trim().isEmpty() ? null : t.trim();
    }

    static Double numero(String t) {
        if (t == null) return null;
        try {
            return Double.valueOf(t.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer entero(String t) {
        Double d = numero(t);
        return d != null ? d.intValue() : null;
    }

    /** El primer número de 8 a 14 dígitos del texto (EAN-8, UPC, EAN-13, GTIN-14), o null. */
    static String codigoDeBarrasEn(String texto) {
        if (texto == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<!\\d)(\\d{8,14})(?!\\d)").matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    /** «30 días», «30», «30 DIAS» → 30. */
    static Integer dias(String t) {
        if (t == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(t);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String sinCeros(String ruc) {
        return ruc == null ? null : ruc.replaceFirst("^0+(?=.)", "");
    }

    private static LocalDateTime fechaHora(String t) {
        if (t == null) return null;
        try {
            return LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t);
        } catch (DateTimeParseException e) {
            LocalDate d = fecha(t);
            return d != null ? d.atStartOfDay() : null;
        }
    }

    private static LocalDate fecha(String t) {
        if (t == null) return null;
        try {
            return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
