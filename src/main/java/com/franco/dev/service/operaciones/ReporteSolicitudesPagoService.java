package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.ReporteMovimientosCajaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.utils.ImageService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.JoinType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reporte PDF de la lista de solicitudes de pago de compras: los mismos filtros y columnas que la tabla del
 * desktop, sin paginar, con el total solicitado por moneda (sin las canceladas).
 */
@Service
@RequiredArgsConstructor
public class ReporteSolicitudesPagoService {

    private static final Logger log = LoggerFactory.getLogger(ReporteSolicitudesPagoService.class);

    static final String PLANTILLA = "reports/solicitudes-pago-lista.jrxml";

    private static final DateTimeFormatter FMT_FILA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** getEstadoDisplayName de solicitud-pago.service.ts del desktop: son dos copias a mano, cambiar las dos. */
    static final Map<SolicitudPagoEstado, String> ESTADO_LABELS = new HashMap<>();

    static {
        ESTADO_LABELS.put(SolicitudPagoEstado.PENDIENTE, "Pendiente");
        ESTADO_LABELS.put(SolicitudPagoEstado.SOLICITADO, "Solicitado");
        ESTADO_LABELS.put(SolicitudPagoEstado.PARCIAL, "Pago Parcial");
        ESTADO_LABELS.put(SolicitudPagoEstado.CONCLUIDO, "Pagada");
        ESTADO_LABELS.put(SolicitudPagoEstado.DEVUELTO, "Devuelta");
        ESTADO_LABELS.put(SolicitudPagoEstado.CANCELADO, "Cancelada");
    }

    private final SolicitudPagoRepository repository;
    private final ProveedorService proveedorService;
    private final MonedaService monedaService;
    private final TesoreriaSecurityService seguridad;
    private final ImageService imageService;

    private volatile JasperReport plantilla;

    /** Fila de la tabla del reporte: todo pre-formateado, la plantilla solo lo ubica. */
    @Data
    @AllArgsConstructor
    public static class Fila {
        private String numero;
        private String proveedor;
        private String fecha;
        private String monto;
        private String estado;
        private Boolean cancelada;
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

    /**
     * @param filtro la misma Specification de la lista paginada (SolicitudPagoGraphQL.specCompras); la consulta
     *               corre acá para que proveedor y moneda (LAZY) se lean dentro de la transacción.
     */
    @Transactional(readOnly = true)
    public String generar(Specification<SolicitudPago> filtro, Long proveedorId, SolicitudPagoEstado estado,
                          String numero, String fechaDesde, String fechaHasta, List<Long> monedaIds) {
        List<SolicitudPago> solicitudes = repository.findAll(filtro.and(conProveedorYMoneda()),
                Sort.by("fechaSolicitud", "id"));
        Proveedor proveedor = proveedorId != null ? proveedorService.findById(proveedorId).orElse(null) : null;
        List<Moneda> monedas = monedaIds == null ? new ArrayList<>() : monedaIds.stream()
                .map(id -> monedaService.findById(id).orElse(null)).filter(Objects::nonNull).collect(Collectors.toList());
        return exportar(armar(solicitudes, proveedor, estado, numero, fechaDesde, fechaHasta, monedas,
                seguridad.currentUsuario()));
    }

    /** Fetch de lo que imprime cada fila: sin esto son consultas por lote de proveedor, persona y moneda. */
    private static Specification<SolicitudPago> conProveedorYMoneda() {
        return (root, query, cb) -> {
            root.fetch("proveedor", JoinType.LEFT).fetch("persona", JoinType.LEFT);
            root.fetch("moneda", JoinType.LEFT);
            return null;
        };
    }

    Contenido armar(List<SolicitudPago> solicitudes, Proveedor proveedor, SolicitudPagoEstado estado, String numero,
                    String fechaDesde, String fechaHasta, List<Moneda> monedas, Usuario usuario) {
        List<Fila> filas = new ArrayList<>();
        Map<String, TotalMoneda> totales = new LinkedHashMap<>();
        for (SolicitudPago s : solicitudes) {
            Moneda mon = s.getMoneda();
            BigDecimal monto = s.getMontoTotal() != null ? BigDecimal.valueOf(s.getMontoTotal()) : BigDecimal.ZERO;
            boolean cancelada = s.getEstado() == SolicitudPagoEstado.CANCELADO;
            filas.add(new Fila(
                    s.getNumeroSolicitud() != null ? s.getNumeroSolicitud() : "-",
                    nombre(s.getProveedor()),
                    s.getFechaSolicitud() != null ? FMT_FILA.format(s.getFechaSolicitud()) : "",
                    conSimbolo(monto, mon),
                    s.getEstado() != null ? ESTADO_LABELS.getOrDefault(s.getEstado(), s.getEstado().name()) : "",
                    cancelada));
            // Confirmado por Franco (14/11): la cancelada se lista pero no suma.
            if (!cancelada) {
                String clave = mon != null && mon.getId() != null ? mon.getId().toString() : "-";
                totales.computeIfAbsent(clave, k -> new TotalMoneda(mon)).sumar(monto);
            }
        }

        Map<String, Object> p = new HashMap<>();
        p.put("titulo", "Solicitudes de pago");
        // null si el servidor no tiene logo: la plantilla lo omite y el reporte sale igual.
        p.put("logo", imageService != null ? imageService.getLogoReporte() : null);
        p.put("numeroFiltro", numero != null && !numero.trim().isEmpty() ? numero.trim().toUpperCase() : "Todos");
        p.put("proveedorFiltro", proveedor != null ? nombre(proveedor) : "Todos");
        p.put("estadoFiltro", estado != null ? ESTADO_LABELS.getOrDefault(estado, estado.name()) : "Todos");
        p.put("monedaFiltro", monedas.isEmpty() ? "Todas"
                : monedas.stream().map(ReporteSolicitudesPagoService::etiquetaMoneda).collect(Collectors.joining(", ")));
        p.put("desdeFiltro", dia(fechaDesde));
        p.put("hastaFiltro", dia(fechaHasta));
        p.put("usuario", nombre(usuario));
        p.put("fechaGeneracion", FMT_GENERADO.format(LocalDateTime.now()));
        p.put("cantidad", String.valueOf(filas.size()));
        p.put("totales", totales.isEmpty()
                ? "Sin solicitudes vigentes para los filtros aplicados."
                : totales.values().stream().map(TotalMoneda::linea).collect(Collectors.joining("\n")));
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
            log.error("Error generando el reporte de solicitudes de pago", e);
            throw new GraphQLException("No se pudo generar el reporte de solicitudes de pago: " + e.getMessage());
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

    /** Los filtros llegan como yyyy-MM-dd, igual que en la lista. */
    private static String dia(String fecha) {
        if (fecha == null || fecha.trim().isEmpty()) return "Sin límite";
        return FMT_DIA.format(LocalDate.parse(fecha.trim()));
    }

    private static String nombre(Proveedor proveedor) {
        if (proveedor == null || proveedor.getPersona() == null || proveedor.getPersona().getNombre() == null) return "-";
        return proveedor.getPersona().getNombre().toUpperCase();
    }

    private static String nombre(Usuario usuario) {
        if (usuario == null) return "-";
        if (usuario.getPersona() != null && usuario.getPersona().getNombre() != null) return usuario.getPersona().getNombre();
        return usuario.getNickname() != null ? usuario.getNickname() : "-";
    }

    private static String etiquetaMoneda(Moneda moneda) {
        return moneda.getDenominacion() + (moneda.getSimbolo() != null ? " (" + moneda.getSimbolo() + ")" : "");
    }

    private static String conSimbolo(BigDecimal valor, Moneda moneda) {
        String monto = ReporteMovimientosCajaService.monto(valor, moneda);
        return moneda != null && moneda.getSimbolo() != null ? moneda.getSimbolo() + " " + monto : monto;
    }

    private static final class TotalMoneda {
        private final Moneda moneda;
        private BigDecimal total = BigDecimal.ZERO;

        TotalMoneda(Moneda moneda) {
            this.moneda = moneda;
        }

        void sumar(BigDecimal monto) {
            total = total.add(monto);
        }

        String linea() {
            return (moneda != null && moneda.getDenominacion() != null ? moneda.getDenominacion() : "Sin moneda")
                    + ": " + conSimbolo(total, moneda);
        }
    }
}
