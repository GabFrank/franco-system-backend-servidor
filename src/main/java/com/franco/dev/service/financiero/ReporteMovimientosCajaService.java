package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reporte PDF de los movimientos de una caja mayor, según la fuente elegida en el dashboard:
 * el efectivo de la caja o una de sus cuentas bancarias. Imprime lo mismo que la tabla
 * (mismos filtros, mismas columnas) pero sin paginar, con un encabezado que resume los filtros,
 * quién lo generó, cuándo, y el total por moneda.
 */
@Service
@RequiredArgsConstructor
public class ReporteMovimientosCajaService {

    private static final Logger log = LoggerFactory.getLogger(ReporteMovimientosCajaService.class);

    static final String PLANTILLA = "reports/movimientos-caja.jrxml";

    private static final DateTimeFormatter FMT_FILA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Mismas etiquetas que el dashboard (ORIGEN_MOVIMIENTO_LABELS en caja-virtual.model.ts). */
    static final Map<String, String> ORIGEN_LABELS = etiquetas(
            "ANULACION", "Anulación",
            "RRHH_VALE", "Vale",
            "RRHH_PRESTAMO", "Préstamo",
            "RRHH_AGUINALDO", "Aguinaldo",
            "RRHH_LIQUIDACION_SUELDO", "Liquidación",
            "RRHH_LIQUIDACION_FINAL", "Finiquito",
            "RETIRO_CAJA", "Retiro de PDV",
            "VENTA_CREDITO_COBRO", "Cobro de crédito",
            "DEVOLUCION", "Devolución",
            "GASTO", "Gasto",
            "ENTRADA_VARIA", "Entrada varia",
            "OPERACION_FINANCIERA", "Operación financiera",
            "PAGO_CPP", "Compra",
            "CHEQUE", "Cheque",
            "ACREDITACION_POS", "Acreditación POS",
            "MALETIN", "Maletín");

    /** tipoMovimientoLabels del dashboard: el fallback cuando el origen no aporta (MANUAL o ausente). */
    static final Map<String, String> TIPO_CAJA_LABELS = etiquetas(
            "INGRESO", "Ingreso",
            "EGRESO", "Egreso",
            "TRANSFERENCIA_ENTRADA", "Transf. Entrada",
            "TRANSFERENCIA_SALIDA", "Transf. Salida",
            "PAGO_PROVEEDOR", "Pago Proveedor",
            "AJUSTE", "Ajuste");

    /** bancoTipoLabels del dashboard. */
    static final Map<String, String> TIPO_BANCO_LABELS = etiquetas(
            "ENTRADA_MANUAL", "Entrada",
            "SALIDA_MANUAL", "Salida",
            "AJUSTE_POSITIVO", "Ajuste +",
            "AJUSTE_NEGATIVO", "Ajuste −",
            "ACREDITACION_POS", "Acreditación POS");

    private final MovimientoCajaVirtualService movimientoCajaVirtualService;
    private final MovimientoBancarioService movimientoBancarioService;
    private final CajaVirtualService cajaVirtualService;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final MonedaService monedaService;
    private final TesoreriaSecurityService seguridad;

    private volatile JasperReport plantilla;

    /** Fila de la tabla del reporte: todo pre-formateado, la plantilla solo lo ubica. */
    @Data
    @AllArgsConstructor
    public static class Fila {
        private String fecha;
        private String responsable;
        private String tipo;
        private String descripcion;
        private String monto;
        private String saldo;
        private Boolean anulado;
    }

    /** Parámetros del encabezado + filas, listos para llenar la plantilla. */
    static class Contenido {
        final Map<String, Object> parametros;
        final List<Fila> filas;

        Contenido(Map<String, Object> parametros, List<Fila> filas) {
            this.parametros = parametros;
            this.filas = filas;
        }
    }

    @Transactional(readOnly = true)
    public String reporteCajaVirtual(Long cajaVirtualId, String desde, String fin, CajaVirtualTipoMovimiento tipo,
                                     Long monedaId, boolean soloActivos) {
        CajaVirtual caja = buscarCaja(cajaVirtualId);
        Moneda moneda = monedaId != null ? monedaService.findById(monedaId).orElse(null) : null;
        List<MovimientoCajaVirtual> movimientos =
                movimientoCajaVirtualService.filterList(cajaVirtualId, desde, fin, tipo, monedaId, soloActivos);
        return exportar(armarCaja(caja, movimientos, desde, fin, tipo, moneda, soloActivos, seguridad.currentUsuario()));
    }

    @Transactional(readOnly = true)
    public String reporteBancario(Long cajaVirtualId, Long cuentaBancariaId, String desde, String fin, String tipo,
                                  boolean soloActivos) {
        CajaVirtual caja = buscarCaja(cajaVirtualId);
        CuentaBancaria cuenta = cuentaBancariaRepository.findById(cuentaBancariaId)
                .orElseThrow(() -> new GraphQLException("Cuenta bancaria no encontrada: " + cuentaBancariaId));
        String tipoValido = MovimientoBancarioService.tipoValido(tipo);
        List<MovimientoBancario> movimientos =
                movimientoBancarioService.filterList(cuentaBancariaId, desde, fin, tipoValido, soloActivos);
        return exportar(armarBanco(caja, cuenta, movimientos, desde, fin, tipoValido, soloActivos, seguridad.currentUsuario()));
    }

    Contenido armarCaja(CajaVirtual caja, List<MovimientoCajaVirtual> movimientos, String desde, String fin,
                        CajaVirtualTipoMovimiento tipo, Moneda moneda, boolean soloActivos, Usuario usuario) {
        List<Fila> filas = new ArrayList<>();
        Map<String, TotalMoneda> totales = new LinkedHashMap<>();
        for (MovimientoCajaVirtual m : movimientos) {
            Moneda mon = m.getMoneda();
            BigDecimal cantidad = m.getCantidad() != null ? BigDecimal.valueOf(m.getCantidad()) : BigDecimal.ZERO;
            boolean anulado = Boolean.FALSE.equals(m.getActivo());
            filas.add(new Fila(
                    fechaFila(m.getCreadoEn()),
                    responsable(m.getUsuario()),
                    etiquetaCaja(m.getOrigenTipo(), m.getTipoMovimiento()),
                    m.getDescripcion(),
                    conSimbolo(monto(cantidad, mon), mon),
                    m.getSaldoPosterior() != null ? conSimbolo(monto(BigDecimal.valueOf(m.getSaldoPosterior()), mon), mon) : "",
                    anulado));
            // El total no cuenta ni el anulado ni su contra-movimiento: sumados se cancelan, pero con
            // "ver anulaciones" apagado el anulado no viene y el contra sí, y el total cambiaría.
            if (!anulado && m.getOrigenTipo() != OrigenMovimientoTipo.ANULACION) {
                total(totales, mon).sumar(TesoreriaService.signedDelta(m.getTipoMovimiento(), cantidad));
            }
        }

        Map<String, Object> p = parametrosComunes(caja, usuario, desde, fin, soloActivos, filas.size(), totales);
        p.put("fuente", "Caja Mayor");
        p.put("tipoFiltro", tipo != null ? TIPO_CAJA_LABELS.getOrDefault(tipo.name(), tipo.name()) : "Todos");
        p.put("monedaFiltro", moneda != null ? etiquetaMoneda(moneda) : "Todas");
        return new Contenido(p, filas);
    }

    Contenido armarBanco(CajaVirtual caja, CuentaBancaria cuenta, List<MovimientoBancario> movimientos,
                         String desde, String fin, String tipo, boolean soloActivos, Usuario usuario) {
        Moneda mon = cuenta.getMoneda();
        List<Fila> filas = new ArrayList<>();
        Map<String, TotalMoneda> totales = new LinkedHashMap<>();
        for (MovimientoBancario m : movimientos) {
            BigDecimal monto = m.getMonto() != null ? m.getMonto() : BigDecimal.ZERO;
            boolean anulado = Boolean.TRUE.equals(m.getAnulado());
            String tipoName = m.getTipoMovimiento() != null ? m.getTipoMovimiento().name() : "";
            filas.add(new Fila(
                    fechaFila(m.getCreadoEn()),
                    responsable(m.getUsuario()),
                    TIPO_BANCO_LABELS.getOrDefault(tipoName, tipoName),
                    m.getDescripcion(),
                    conSimbolo(monto(monto, mon), mon),
                    m.getSaldoPosterior() != null ? conSimbolo(monto(m.getSaldoPosterior(), mon), mon) : "",
                    anulado));
            if (!anulado && !OrigenMovimientoTipo.ANULACION.name().equals(m.getOrigenTipo())) {
                BigDecimal efecto = BancoLedgerService.esEgreso(m.getTipoMovimiento()) ? monto.abs().negate() : monto.abs();
                total(totales, mon).sumar(efecto);
            }
        }

        Map<String, Object> p = parametrosComunes(caja, usuario, desde, fin, soloActivos, filas.size(), totales);
        String banco = cuenta.getBanco() != null && cuenta.getBanco().getNombre() != null ? cuenta.getBanco().getNombre() : "Banco";
        p.put("fuente", banco + " - " + (cuenta.getNumero() != null ? cuenta.getNumero() : ""));
        p.put("tipoFiltro", tipo != null ? TIPO_BANCO_LABELS.getOrDefault(tipo, tipo) : "Todos");
        p.put("monedaFiltro", mon != null ? etiquetaMoneda(mon) : "-");
        return new Contenido(p, filas);
    }

    JasperPrint llenar(Contenido contenido) throws JRException {
        return JasperFillManager.fillReport(plantilla(), new HashMap<>(contenido.parametros),
                new JRBeanCollectionDataSource(contenido.filas));
    }

    private String exportar(Contenido contenido) {
        try {
            return Base64.getEncoder().encodeToString(JasperExportManager.exportReportToPdf(llenar(contenido)));
        } catch (JRException e) {
            log.error("Error generando el reporte de movimientos de caja", e);
            throw new GraphQLException("No se pudo generar el reporte de movimientos: " + e.getMessage());
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

    private CajaVirtual buscarCaja(Long cajaVirtualId) {
        return cajaVirtualService.findById(cajaVirtualId)
                .orElseThrow(() -> new GraphQLException("Caja no encontrada: " + cajaVirtualId));
    }

    private Map<String, Object> parametrosComunes(CajaVirtual caja, Usuario usuario, String desde, String fin,
                                                  boolean soloActivos, int cantidad, Map<String, TotalMoneda> totales) {
        Map<String, Object> p = new HashMap<>();
        p.put("titulo", "Movimientos de " + (caja.getNombre() != null ? caja.getNombre() : "caja"));
        LocalDateTime inicio = MovimientoCajaVirtualService.inicioRango(desde);
        LocalDateTime finRango = MovimientoCajaVirtualService.finRango(fin);
        p.put("desdeFiltro", inicio != null ? FMT_DIA.format(inicio) : "Sin límite");
        p.put("hastaFiltro", finRango != null ? FMT_DIA.format(finRango) : "Sin límite");
        p.put("anuladosFiltro", soloActivos ? "No incluidos" : "Incluidos (tachados)");
        p.put("usuario", responsable(usuario));
        p.put("fechaGeneracion", FMT_GENERADO.format(LocalDateTime.now()));
        p.put("cantidad", String.valueOf(cantidad));
        p.put("totales", totales.isEmpty()
                ? "Sin movimientos vigentes para los filtros aplicados."
                : totales.values().stream().map(TotalMoneda::linea).collect(Collectors.joining("\n")));
        return p;
    }

    static String etiquetaCaja(OrigenMovimientoTipo origen, CajaVirtualTipoMovimiento tipo) {
        String porOrigen = origen != null ? ORIGEN_LABELS.get(origen.name()) : null;
        if (porOrigen != null) return porOrigen;
        return tipo != null ? TIPO_CAJA_LABELS.getOrDefault(tipo.name(), tipo.name()) : "";
    }

    private static String fechaFila(LocalDateTime fecha) {
        return fecha != null ? FMT_FILA.format(fecha) : "";
    }

    private static String responsable(Usuario usuario) {
        if (usuario == null) return "-";
        if (usuario.getPersona() != null && usuario.getPersona().getNombre() != null) return usuario.getPersona().getNombre();
        return usuario.getNickname() != null ? usuario.getNickname() : "-";
    }

    private static String etiquetaMoneda(Moneda moneda) {
        if (moneda == null) return "Sin moneda";
        return moneda.getDenominacion() + (moneda.getSimbolo() != null ? " (" + moneda.getSimbolo() + ")" : "");
    }

    private static String conSimbolo(String monto, Moneda moneda) {
        return moneda != null && moneda.getSimbolo() != null ? monto + " " + moneda.getSimbolo() : monto;
    }

    /**
     * Decimales de la moneda con el mismo fallback que el dashboard (formatoDe): el guaraní no lleva
     * fracción, las demás sí. Separadores paraguayos: punto de miles, coma decimal.
     */
    static String monto(BigDecimal valor, Moneda moneda) {
        int decimales = moneda != null && moneda.getDecimales() != null
                ? moneda.getDecimales()
                : (moneda != null && moneda.getDenominacion() != null
                        && moneda.getDenominacion().toUpperCase().contains("GUARAN") ? 0 : 2);
        String patron = decimales <= 0 ? "#,##0" : "#,##0." + "0".repeat(decimales);
        return new DecimalFormat(patron, new DecimalFormatSymbols(Locale.GERMANY)).format(valor);
    }

    private static TotalMoneda total(Map<String, TotalMoneda> totales, Moneda moneda) {
        String clave = moneda != null && moneda.getId() != null ? moneda.getId().toString() : "-";
        return totales.computeIfAbsent(clave, k -> new TotalMoneda(moneda));
    }

    /** Ingresos, egresos y neto de una moneda, sobre los movimientos vigentes. */
    private static final class TotalMoneda {
        private final Moneda moneda;
        private BigDecimal ingresos = BigDecimal.ZERO;
        private BigDecimal egresos = BigDecimal.ZERO;

        TotalMoneda(Moneda moneda) {
            this.moneda = moneda;
        }

        void sumar(BigDecimal efecto) {
            if (efecto.signum() >= 0) ingresos = ingresos.add(efecto);
            else egresos = egresos.add(efecto.negate());
        }

        /** Una línea por moneda; cada importe lleva su símbolo, así se lee sin mirar el encabezado. */
        String linea() {
            return "Ingresos: " + conSimbolo(monto(ingresos, moneda), moneda)
                    + " - Egresos: " + conSimbolo(monto(egresos, moneda), moneda)
                    + " - Total: " + conSimbolo(monto(ingresos.subtract(egresos), moneda), moneda);
        }
    }

    private static Map<String, String> etiquetas(String... claveValor) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) m.put(claveValor[i], claveValor[i + 1]);
        return m;
    }
}
