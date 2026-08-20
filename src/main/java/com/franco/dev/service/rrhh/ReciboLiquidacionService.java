package com.franco.dev.service.rrhh;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.repository.rrhh.PenalizacionRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.VacacionVentaRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.service.rrhh.dto.ReciboLiquidacionItemDto;
import com.franco.dev.utilitarios.NumeroALetrasService;
import graphql.GraphQLException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genera el recibo de sueldo (PDF base64) de una liquidación vía JasperReports.
 * Patrón: cargar .jrxml → compilar → fill con params + datasource → export PDF → base64.
 */
@Service
public class ReciboLiquidacionService {

    private static final Locale ES = new Locale("es", "PY");
    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LiquidacionSueldoService liquidacionSueldoService;
    private final ConfiguracionGeneralService configuracionGeneralService;
    private final NumeroALetrasService numeroALetrasService;
    private final ValeRepository valeRepository;
    private final BonoRepository bonoRepository;
    private final VacacionVentaRepository vacacionVentaRepository;
    private final PrestamoCuotaRepository prestamoCuotaRepository;
    private final PenalizacionRepository penalizacionRepository;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final DecimalFormat formato = new DecimalFormat("#,##0.##");
    private final DecimalFormat formatoGs = new DecimalFormat("#,##0");   // guaraníes sin decimales (ticket)

    public ReciboLiquidacionService(LiquidacionSueldoService liquidacionSueldoService,
                                    ConfiguracionGeneralService configuracionGeneralService,
                                    NumeroALetrasService numeroALetrasService,
                                    ValeRepository valeRepository,
                                    BonoRepository bonoRepository,
                                    VacacionVentaRepository vacacionVentaRepository,
                                    PrestamoCuotaRepository prestamoCuotaRepository,
                                    PenalizacionRepository penalizacionRepository,
                                    ConfiguracionRrhhService configuracionRrhhService) {
        this.liquidacionSueldoService = liquidacionSueldoService;
        this.configuracionGeneralService = configuracionGeneralService;
        this.numeroALetrasService = numeroALetrasService;
        this.valeRepository = valeRepository;
        this.bonoRepository = bonoRepository;
        this.vacacionVentaRepository = vacacionVentaRepository;
        this.prestamoCuotaRepository = prestamoCuotaRepository;
        this.penalizacionRepository = penalizacionRepository;
        this.configuracionRrhhService = configuracionRrhhService;
    }

    @Transactional(readOnly = true)
    public String generarBase64(Long liquidacionId, Integer anchoMm, boolean escpos) {
        LiquidacionSueldo liq = liquidacionSueldoService.findById(liquidacionId)
                .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));

        // Ticket ESC/POS (payload crudo para print-local del cliente).
        if (escpos) {
            return generarTicketEscPos(liq, anchoMm);
        }
        // Formato ticket PDF (preview angosto en el visor).
        if (anchoMm != null) {
            return generarTicket(liq, anchoMm);
        }

        List<ReciboLiquidacionItemDto> filas = new ArrayList<>();
        for (LiquidacionItem it : itemsParaImpresion(liquidacionId)) {
            filas.add(new ReciboLiquidacionItemDto(
                    operacion(it),
                    it.getTipo() == LiquidacionItemTipo.HABER ? "ENTRADA" : "SALIDA",
                    it.getDescripcion(),
                    fechaItem(it, liq),
                    formatear(it.getMonto())));
        }
        // JasperReports omite el detail si el datasource está vacío
        if (filas.isEmpty()) {
            filas.add(new ReciboLiquidacionItemDto("-", "", "SIN ITEMS", "", "0"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("empresa", razonSocial());
        params.put("ruc", ruc());
        params.put("direccionEmpresa", configuracionRrhhService.getString("EMPRESA_DIRECCION", ""));
        params.put("telefonoEmpresa", configuracionRrhhService.getString("EMPRESA_TELEFONO", ""));
        params.put("funcionario", nombreFuncionario(liq));
        params.put("documento", documentoFuncionario(liq));
        params.put("direccion", direccionFuncionario(liq));
        params.put("cargo", liq.getFuncionario() != null && liq.getFuncionario().getCargo() != null
                ? liq.getFuncionario().getCargo().getNombre() : null);
        params.put("periodo", liq.getPeriodo());
        params.put("fecha", fechaEnPalabras());
        params.put("ciudad", ciudad(liq));
        params.put("sueldoBase", formatear(liq.getSalarioBase()));
        params.put("totalRecibido", formatear(liq.getTotalHaberes()));
        params.put("totalDescontado", formatear(liq.getTotalDescuentos()));
        params.put("totalNeto", formatear(liq.getTotalNeto()));
        // El monto en letras acompania la frase de recepcion, que declara el BRUTO
        // ("recibi la suma de..."). El neto sigue mostrandose aparte, en "Total a cobrar".
        params.put("montoEnLetras", enLetras(liq.getTotalHaberes()));

        try (InputStream jrxmlStream = new ClassPathResource("reports/recibo-liquidacion.jrxml").getInputStream()) {
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filas);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return java.util.Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error generando el recibo: " + e.getMessage());
        }
    }

    /** Recibo de sueldo en ESC/POS (base64) para impresión térmica local del cliente. */
    private String generarTicketEscPos(LiquidacionSueldo liq, Integer anchoMm) {
        List<com.franco.dev.utilitarios.print.ReciboTicketEscPos.Row> rows = new ArrayList<>();
        for (LiquidacionItem it : itemsParaImpresion(liq.getId())) {
            rows.add(new com.franco.dev.utilitarios.print.ReciboTicketEscPos.Row(
                    conceptoTicket(it), montoTicket(it)));
        }
        BigDecimal neto = liq.getTotalNeto() != null ? liq.getTotalNeto() : BigDecimal.ZERO;
        return com.franco.dev.utilitarios.print.ReciboTicketEscPos.build(
                razonSocial(), "RECIBO DE SUELDO " + (liq.getPeriodo() != null ? liq.getPeriodo() : ""),
                nombreFuncionario(liq), documentoFuncionario(liq), LocalDate.now().toString(),
                rows, formatoGs.format(neto), enLetras(neto),
                "Recibi conforme, en concepto de haberes del periodo,", anchoMm);
    }

    /** Recibo de sueldo en formato ticket (58/80mm), plantilla genérica concepto/monto. */
    private String generarTicket(LiquidacionSueldo liq, Integer anchoMm) {
        List<ReporteRrhhService.FiniquitoRow> filas = new ArrayList<>();
        for (LiquidacionItem it : itemsParaImpresion(liq.getId())) {
            filas.add(new ReporteRrhhService.FiniquitoRow(conceptoTicket(it), montoTicket(it)));
        }
        if (filas.isEmpty()) filas.add(new ReporteRrhhService.FiniquitoRow("SIN ITEMS", "0"));

        BigDecimal neto = liq.getTotalNeto() != null ? liq.getTotalNeto() : BigDecimal.ZERO;
        Map<String, Object> params = new HashMap<>();
        params.put("empresa", razonSocial());
        params.put("titulo", "RECIBO DE SUELDO " + (liq.getPeriodo() != null ? liq.getPeriodo() : ""));
        params.put("funcionario", nombreFuncionario(liq));
        params.put("documento", documentoFuncionario(liq));
        params.put("fecha", LocalDate.now().toString());
        params.put("clausula", "Recibí conforme, en concepto de haberes del período,");
        params.put("total", formatoGs.format(neto));
        params.put("totalEnLetras", enLetras(neto));

        String tpl = anchoMm >= 80 ? "reports/recibo-ticket-80.jrxml" : "reports/recibo-ticket-58.jrxml";
        try (InputStream jrxmlStream = new ClassPathResource(tpl).getInputStream()) {
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(filas);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
            return java.util.Base64.getEncoder().encodeToString(pdfBytes);
        } catch (Exception e) {
            throw new GraphQLException("Error generando el recibo (ticket): " + e.getMessage());
        }
    }

    /**
     * Items tal como se imprimen, para los tres formatos (PDF A4, ticket PDF y ESC/POS).
     *
     * <p>Punto unico donde se aplica cualquier politica de presentacion: agrupar varios
     * items en una linea o desglosar uno en varias. Hoy devuelve los items tal cual.</p>
     *
     * <p>Existe porque los tres generadores construyen sus filas por separado y con
     * modelos distintos — el A4 tiene 5 columnas y marca el signo con ENTRADA/SALIDA, los
     * tickets tienen 2 y lo marcan con parentesis. Sin este punto en comun, cada cambio de
     * presentacion hay que escribirlo tres veces y alcanza con olvidarse de uno para que
     * el ticket termico y el PDF muestren cosas distintas de la misma liquidacion.</p>
     */
    private List<LiquidacionItem> itemsParaImpresion(Long liquidacionId) {
        return liquidacionSueldoService.findItems(liquidacionId);
    }

    /** Concepto de una fila de ticket: la descripcion del item, o su categoria si no tiene. */
    private String conceptoTicket(LiquidacionItem it) {
        return it.getDescripcion() != null ? it.getDescripcion() : operacion(it);
    }

    /** Monto de una fila de ticket. Los descuentos van entre parentesis (no hay columna de signo). */
    private String montoTicket(LiquidacionItem it) {
        String monto = formatoGs.format(it.getMonto() != null ? it.getMonto() : BigDecimal.ZERO);
        return it.getTipo() == LiquidacionItemTipo.DESCUENTO ? "(" + monto + ")" : monto;
    }

    /** Categoria del movimiento a partir del codigo del item, como en el recibo modelo. */
    private String operacion(LiquidacionItem it) {
        String c = it.getCodigo() != null ? it.getCodigo() : "";
        switch (c) {
            case "SALARIO_BASE": return "SUELDO";
            case "HORA_EXTRA": return "HORAS EXTRA";
            case "BONO_MANUAL": return "BONUS";
            case "IPS_DESCUENTO": return "IPS";
            case "VALE_DESCUENTO":
            case "ADELANTO_DESCUENTO": return "ANTICIPOS";
            case "PRESTAMO_CUOTA": return "PRESTAMO";
            case "VACACION_VENTA": return "VACACIONES";
            case "AGUINALDO": return "AGUINALDO";
            case "CREDITO_CONVENIO_CUOTA": return "CREDITO";
            case "PENALIZACION":
            case "JUSTIFICATIVO_DESCUENTO": return "DESCUENTOS";
            default: return Boolean.TRUE.equals(it.getManual()) ? "AJUSTE"
                    : (it.getTipo() == LiquidacionItemTipo.HABER ? "HABER" : "DESCUENTO");
        }
    }

    /**
     * Fecha del movimiento: la del registro origen cuando existe (cuando se tomo el vale,
     * el bono, etc.), o el fin del periodo para los conceptos calculados (sueldo, IPS).
     */
    private String fechaItem(LiquidacionItem it, LiquidacionSueldo liq) {
        LocalDate f = null;
        Long ref = it.getReferenciaId();
        String tipo = it.getReferenciaTipo();
        if (ref != null && tipo != null) {
            switch (tipo) {
                case "VALE":
                case "ADELANTO":
                    f = valeRepository.findById(ref).map(v -> v.getFecha()).orElse(null);
                    break;
                case "BONO":
                    f = bonoRepository.findById(ref).map(b -> b.getFecha()).orElse(null);
                    break;
                case "VACACION_VENTA":
                    f = vacacionVentaRepository.findById(ref).map(v -> v.getFecha()).orElse(null);
                    break;
                case "CPP_CUOTA":
                    f = prestamoCuotaRepository.findById(ref).map(c -> c.getFechaVencimiento()).orElse(null);
                    break;
                case "PENALIZACION":
                    f = penalizacionRepository.findById(ref).map(p -> p.getFecha()).orElse(null);
                    break;
                default:
                    break;
            }
        }
        // Concepto calculado (sueldo, IPS, HE): no ocurre un dia puntual. Si la
        // liquidacion ya se pago, la fecha significativa es la del desembolso; si
        // todavia no, el fin del periodo (no inventamos una fecha de pago inexistente).
        if (f == null) {
            f = liq.getFechaPago() != null ? liq.getFechaPago().toLocalDate() : liq.getFechaFin();
        }
        return f != null ? f.format(DDMMYYYY) : "";
    }

    private String formatear(BigDecimal valor) {
        return formato.format(valor != null ? valor : BigDecimal.ZERO);
    }

    /** Parte entera del neto en palabras (guaranies, sin centavos). */
    private String enLetras(BigDecimal neto) {
        BigDecimal v = neto != null ? neto : BigDecimal.ZERO;
        String entero = v.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
        String palabras = numeroALetrasService.converter(entero, true);
        return palabras != null ? palabras.trim() + " GUARANIES" : "";
    }

    /** Fecha de hoy en palabras: "JUEVES 23 JULIO 2026". */
    private String fechaEnPalabras() {
        LocalDate hoy = LocalDate.now();
        String dia = hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, ES).toUpperCase();
        String mes = hoy.getMonth().getDisplayName(TextStyle.FULL, ES).toUpperCase();
        return dia + " " + hoy.getDayOfMonth() + " " + mes + " " + hoy.getYear();
    }

    private String razonSocial() {
        ConfiguracionGeneral cg = configGeneral();
        if (cg != null) {
            if (cg.getRazonSocial() != null) return cg.getRazonSocial();
            if (cg.getNombreEmpresa() != null) return cg.getNombreEmpresa();
        }
        return "";
    }

    /** RUC de la empresa emisora, para el encabezado del recibo. */
    private String ruc() {
        ConfiguracionGeneral cg = configGeneral();
        return cg != null && cg.getRuc() != null ? cg.getRuc() : "";
    }

    private ConfiguracionGeneral configGeneral() {
        List<ConfiguracionGeneral> all = configuracionGeneralService.findAll2();
        return all != null && !all.isEmpty() ? all.get(0) : null;
    }

    private String ciudad(LiquidacionSueldo liq) {
        Funcionario f = liq.getFuncionario();
        if (f != null && f.getSucursal() != null && f.getSucursal().getCiudad() != null
                && f.getSucursal().getCiudad().getDescripcion() != null) {
            return f.getSucursal().getCiudad().getDescripcion();
        }
        return "";
    }

    private String nombreFuncionario(LiquidacionSueldo liq) {
        if (liq.getFuncionario() != null && liq.getFuncionario().getPersona() != null
                && liq.getFuncionario().getPersona().getNombre() != null) {
            return liq.getFuncionario().getPersona().getNombre();
        }
        return "";
    }

    private String documentoFuncionario(LiquidacionSueldo liq) {
        if (liq.getFuncionario() != null && liq.getFuncionario().getPersona() != null) {
            return liq.getFuncionario().getPersona().getDocumento();
        }
        return "";
    }

    private String direccionFuncionario(LiquidacionSueldo liq) {
        if (liq.getFuncionario() != null && liq.getFuncionario().getPersona() != null) {
            return liq.getFuncionario().getPersona().getDireccion();
        }
        return "";
    }
}
