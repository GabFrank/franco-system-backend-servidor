package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.dto.ResumenFiscalContribuyente;
import com.franco.dev.domain.financiero.dto.ResumenFiscalRubro;
import com.franco.dev.domain.financiero.dto.ResumenFiscalTimbrado;
import com.franco.dev.domain.financiero.dto.ResumenFiscalVentas;
import com.franco.dev.domain.personas.Usuario;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PDF del resumen fiscal de ventas (resumen-fiscal-ventas.jrxml). Una pasada de la plantilla por
 * RUC, exportadas juntas en un solo PDF.
 */
@Service
@RequiredArgsConstructor
public class ReporteResumenFiscalVentasService {

    private static final Logger log = LoggerFactory.getLogger(ReporteResumenFiscalVentasService.class);

    static final String PLANTILLA = "reports/resumen-fiscal-ventas.jrxml";

    private static final DateTimeFormatter FMT_GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ResumenFiscalVentasService resumenFiscalVentasService;
    private final FacturacionSecurityService seguridad;

    private volatile JasperReport plantilla;

    @Data
    @AllArgsConstructor
    public static class FilaResumen {
        private String concepto;
        private String monto;
        private String iva;
        private Boolean total;
    }

    @Data
    @AllArgsConstructor
    public static class FilaRubro {
        /** Vacio cuando repite el rubro de la fila anterior. */
        private String rubro;
        private String inciso;
        private String concepto;
        private String gravada10;
        private String gravada5;
        private String iva10;
        private String iva5;
        private String exentas;
    }

    @Data
    @AllArgsConstructor
    public static class FilaDetalle {
        private String sucursal;
        private String tipo;
        private String rango;
        private String emitidas;
        private String anuladas;
        private String fueraDeVigencia;
        private String gravada10;
        private String iva10;
        private String gravada5;
        private String iva5;
        private String exentas;
        private String totalFacturado;
        private Boolean total;
    }

    /** Parametros + filas de detalle de un RUC, listos para llenar la plantilla. */
    static class Contenido {
        final Map<String, Object> parametros;
        final List<FilaResumen> resumen;
        final List<FilaRubro> rubros;
        final List<FilaDetalle> detalle;

        Contenido(Map<String, Object> parametros, List<FilaResumen> resumen, List<FilaRubro> rubros,
                  List<FilaDetalle> detalle) {
            this.parametros = parametros;
            this.resumen = resumen;
            this.rubros = rubros;
            this.detalle = detalle;
        }
    }

    @Transactional(readOnly = true)
    public String pdf(Integer anio, Integer mes, List<Long> sucIds) {
        ResumenFiscalVentas resumen = resumenFiscalVentasService.resumen(anio, mes, sucIds);
        if (resumen.getContribuyentes().isEmpty()) {
            throw new GraphQLException("No hay facturas en " + resumen.getPeriodo() + " para las sucursales elegidas.");
        }
        String usuario = responsable(seguridad.currentUsuario());
        String generado = FMT_GENERADO.format(LocalDateTime.now());
        List<Contenido> contenidos = new ArrayList<>();
        for (ResumenFiscalContribuyente c : resumen.getContribuyentes()) {
            contenidos.add(armar(resumen, c, usuario, generado));
        }
        return exportar(contenidos);
    }

    static Contenido armar(ResumenFiscalVentas resumen, ResumenFiscalContribuyente c, String usuario,
                           String generado) {
        Map<String, Object> p = new HashMap<>();
        p.put("razonSocial", c.getRazonSocial());
        p.put("ruc", c.getRuc());
        p.put("periodo", resumen.getPeriodo());
        p.put("sucursalesFiltro", resumen.getSucursalesFiltro());
        p.put("usuario", usuario);
        p.put("fechaGeneracion", generado);
        p.put("totalFacturado", gs(c.getTotalFacturado()));
        p.put("emitidas", entero(c.getEmitidas()));
        p.put("anuladas", entero(c.getAnuladas()));
        p.put("avisoVigencia", c.getFueraDeVigencia() != null && c.getFueraDeVigencia() > 0
                ? "Atención: " + entero(c.getFueraDeVigencia())
                + " factura(s) se emitieron fuera de la vigencia de su timbrado (columna F. vig.)."
                : null);

        List<FilaResumen> filasResumen = new ArrayList<>();
        filasResumen.add(new FilaResumen("Gravadas 10%", gs(c.getGravada10()), gs(c.getIva10()), false));
        filasResumen.add(new FilaResumen("Gravadas 5%", gs(c.getGravada5()), gs(c.getIva5()), false));
        filasResumen.add(new FilaResumen("Exentas", gs(c.getExentas()), "", false));
        filasResumen.add(new FilaResumen("Total", gs(c.getTotalBase()), gs(c.getTotalIva()), true));

        List<FilaRubro> filasRubro = new ArrayList<>();
        String rubroAnterior = null;
        for (ResumenFiscalRubro r : c.getRubros()) {
            filasRubro.add(new FilaRubro(r.getRubro().equals(rubroAnterior) ? "" : r.getRubro(), r.getInciso(),
                    r.getConcepto(), gs(r.getGravada10()), gs(r.getGravada5()), gs(r.getIva10()),
                    gs(r.getIva5()), gs(r.getExentas())));
            rubroAnterior = r.getRubro();
        }

        List<FilaDetalle> filasDetalle = new ArrayList<>();
        for (ResumenFiscalTimbrado d : c.getDetalle()) {
            filasDetalle.add(new FilaDetalle(
                    d.getSucursal() + " / " + d.getTimbrado(),
                    d.getTipo(),
                    d.getNumeroDesde() + " al " + d.getNumeroHasta(),
                    entero(d.getEmitidas()),
                    entero(d.getAnuladas()),
                    d.getFueraDeVigencia() != null && d.getFueraDeVigencia() > 0 ? entero(d.getFueraDeVigencia()) : "",
                    gs(d.getGravada10()), gs(d.getIva10()), gs(d.getGravada5()), gs(d.getIva5()),
                    gs(d.getExentas()), gs(d.getTotalFacturado()), false));
        }
        filasDetalle.add(new FilaDetalle("Total", "", "", entero(c.getEmitidas()), entero(c.getAnuladas()),
                c.getFueraDeVigencia() != null && c.getFueraDeVigencia() > 0 ? entero(c.getFueraDeVigencia()) : "",
                gs(c.getGravada10()), gs(c.getIva10()), gs(c.getGravada5()), gs(c.getIva5()),
                gs(c.getExentas()), gs(c.getTotalFacturado()), true));

        return new Contenido(p, filasResumen, filasRubro, filasDetalle);
    }

    JasperPrint llenar(Contenido contenido) throws JRException {
        Map<String, Object> p = new HashMap<>(contenido.parametros);
        p.put("resumenDS", new JRBeanCollectionDataSource(contenido.resumen));
        p.put("rubrosDS", new JRBeanCollectionDataSource(contenido.rubros));
        return JasperFillManager.fillReport(plantilla(), p, new JRBeanCollectionDataSource(contenido.detalle));
    }

    byte[] exportarBytes(List<Contenido> contenidos) throws JRException {
        List<JasperPrint> prints = new ArrayList<>();
        for (Contenido contenido : contenidos) {
            prints.add(llenar(contenido));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(SimpleExporterInput.getInstance(prints));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    private String exportar(List<Contenido> contenidos) {
        try {
            return Base64.getEncoder().encodeToString(exportarBytes(contenidos));
        } catch (JRException e) {
            log.error("Error generando el resumen fiscal de ventas", e);
            throw new GraphQLException("No se pudo generar el resumen fiscal de ventas: " + e.getMessage());
        }
    }

    private JasperReport plantilla() throws JRException {
        JasperReport compilada = plantilla;
        if (compilada == null) {
            try (InputStream is = new ClassPathResource(PLANTILLA).getInputStream()) {
                compilada = JasperCompileManager.compileReport(is);
            } catch (IOException e) {
                throw new JRException("No se encontró la plantilla " + PLANTILLA, e);
            }
            plantilla = compilada;
        }
        return compilada;
    }

    /** Guaranies sin decimales con punto de miles: 206.237.537. */
    static String gs(Double valor) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(new Locale("es", "PY")));
        DecimalFormatSymbols s = f.getDecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        f.setDecimalFormatSymbols(s);
        return f.format(valor != null ? valor : 0d);
    }

    private static String entero(Long valor) {
        return gs(valor != null ? valor.doubleValue() : 0d);
    }

    private static String responsable(Usuario usuario) {
        if (usuario == null) {
            return "-";
        }
        if (usuario.getPersona() != null && usuario.getPersona().getNombre() != null) {
            return usuario.getPersona().getNombre();
        }
        return usuario.getNickname() != null ? usuario.getNickname() : "-";
    }
}
