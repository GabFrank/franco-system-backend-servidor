package com.franco.dev.service.grafico;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.EstadoVentaCredito;
import com.franco.dev.graphql.grafico.dto.ConvenioConteo;
import com.franco.dev.graphql.grafico.dto.ConvenioSucursalIntegridad;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Convenios (ventas a crédito) por sucursal: conteo por estado en el central y comparación contra la
 * base de cada filial, para detectar convenios que no llegaron por réplica (venta_credito viaja
 * filial → central).
 *
 * La filial se consulta en vivo, solo lectura y con timeouts cortos: una filial caída queda como
 * SIN_CONEXION sin colgar el request. Corre una consulta a la vez para no multiplicar la carga sobre
 * las filiales con clicks repetidos.
 */
@Service
public class ConvenioIntegridadService {

    private static final Logger log = LoggerFactory.getLogger(ConvenioIntegridadService.class);

    static final int TOPE_IDS = 50;
    private static final int HILOS = 6;
    private static final long ESPERA_TOTAL_SEG = 25;
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SQL_FILAS =
            "SELECT id, sucursal_id, estado::text AS estado, creado_en FROM financiero.venta_credito " +
            "WHERE creado_en >= ? AND creado_en < ? AND sucursal_id ";

    private final JdbcTemplate jdbcTemplate;
    private final SucursalService sucursalService;
    private final LogicalReplicationService replicationService;
    private final ExecutorService pool = Executors.newFixedThreadPool(HILOS);
    private final ReentrantLock enCurso = new ReentrantLock();

    @Value("${spring.datasource.username:franco}")
    private String dbUsername;

    @Value("${spring.datasource.password:franco}")
    private String dbPassword;

    public ConvenioIntegridadService(JdbcTemplate jdbcTemplate, SucursalService sucursalService,
                                     LogicalReplicationService replicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sucursalService = sucursalService;
        this.replicationService = replicationService;
    }

    @PreDestroy
    void cerrar() {
        pool.shutdownNow();
    }

    /** Una fila de venta_credito con lo justo para contar y comparar. */
    static final class Fila {
        final long id;
        final long sucursalId;
        final String estado;
        final LocalDateTime creadoEn;

        Fila(long id, long sucursalId, String estado, LocalDateTime creadoEn) {
            this.id = id;
            this.sucursalId = sucursalId;
            this.estado = estado;
            this.creadoEn = creadoEn;
        }
    }

    public List<ConvenioSucursalIntegridad> porSucursal(String fechaInicio, String fechaFin,
                                                        EstadoVentaCredito estado, List<Long> sucIds) {
        LocalDateTime desde = parsearFecha(fechaInicio, "fechaInicio").atStartOfDay();
        LocalDateTime hasta = parsearFecha(fechaFin, "fechaFin").plusDays(1).atStartOfDay();
        if (!hasta.isAfter(desde)) {
            throw new GraphQLException("La fecha hasta no puede ser anterior a la fecha desde.");
        }
        if (!enCurso.tryLock()) {
            throw new GraphQLException("Ya hay una consulta de integridad de convenios en curso. Esperá a que termine.");
        }
        try {
            Set<Long> filtro = sucIds == null ? Collections.emptySet()
                    : sucIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
            List<Sucursal> sucursales = sucursalService.findAllExcludingServer().stream()
                    .filter(s -> filtro.isEmpty() || filtro.contains(s.getId()))
                    .collect(Collectors.toList());
            if (sucursales.isEmpty()) return new ArrayList<>();

            Map<Long, List<Fila>> central = filasCentral(sucursales, desde, hasta);
            String baseFilial = replicationService.getBranchDbName();

            Map<Long, Future<List<Fila>>> futuros = new LinkedHashMap<>();
            for (Sucursal s : sucursales) {
                if (tieneIp(s)) {
                    futuros.put(s.getId(), pool.submit(() -> filasFilial(s, baseFilial, desde, hasta)));
                }
            }

            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_TOTAL_SEG);
            List<ConvenioSucursalIntegridad> res = new ArrayList<>();
            for (Sucursal s : sucursales) {
                List<Fila> filasCentral = central.getOrDefault(s.getId(), Collections.emptyList());
                if (!tieneIp(s)) {
                    res.add(sinFilial(s, filasCentral, estado, ConvenioSucursalIntegridad.SIN_IP,
                            "La sucursal no tiene IP o puerto configurado"));
                    continue;
                }
                Future<List<Fila>> f = futuros.get(s.getId());
                try {
                    long resta = Math.max(0, limite - System.nanoTime());
                    List<Fila> filasFilial = f.get(resta, TimeUnit.NANOSECONDS);
                    res.add(comparar(s.getId(), s.getNombre(), filasCentral, filasFilial, estado));
                } catch (TimeoutException e) {
                    f.cancel(true);
                    res.add(sinFilial(s, filasCentral, estado, ConvenioSucursalIntegridad.SIN_CONEXION,
                            "Sin respuesta (timeout)"));
                } catch (ExecutionException e) {
                    log.warn("Convenios: no se pudo consultar la filial {}: {}", s.getId(), e.getCause().toString());
                    res.add(sinFilial(s, filasCentral, estado, ConvenioSucursalIntegridad.SIN_CONEXION,
                            categoriaError(e.getCause())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GraphQLException("Consulta interrumpida.");
                }
            }
            return res;
        } finally {
            enCurso.unlock();
        }
    }

    private static boolean tieneIp(Sucursal s) {
        return s.getIp() != null && !s.getIp().trim().isEmpty() && s.getPuerto() != null;
    }

    private static LocalDate parsearFecha(String valor, String campo) {
        try {
            return LocalDate.parse(valor == null ? "" : valor.trim());
        } catch (DateTimeParseException e) {
            throw new GraphQLException("Fecha inválida en " + campo + ": se espera yyyy-MM-dd.");
        }
    }

    private Map<Long, List<Fila>> filasCentral(List<Sucursal> sucursales, LocalDateTime desde, LocalDateTime hasta) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(desde));
        args.add(Timestamp.valueOf(hasta));
        sucursales.forEach(s -> args.add(s.getId()));
        String marcas = sucursales.stream().map(s -> "?").collect(Collectors.joining(","));
        List<Fila> filas = jdbcTemplate.query(SQL_FILAS + "IN (" + marcas + ")",
                (rs, i) -> leerFila(rs), args.toArray());
        return filas.stream().collect(Collectors.groupingBy(f -> f.sucursalId));
    }

    private List<Fila> filasFilial(Sucursal s, String baseFilial, LocalDateTime desde, LocalDateTime hasta)
            throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s", s.getIp().trim(), s.getPuerto(), baseFilial);
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        props.setProperty("connectTimeout", "3");
        props.setProperty("loginTimeout", "3");
        props.setProperty("socketTimeout", "20");
        props.setProperty("ApplicationName", "frc-central-convenios");
        props.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=15000");
        try (Connection con = DriverManager.getConnection(url, props)) {
            con.setReadOnly(true);
            try (PreparedStatement ps = con.prepareStatement(SQL_FILAS + "= ?")) {
                ps.setTimestamp(1, Timestamp.valueOf(desde));
                ps.setTimestamp(2, Timestamp.valueOf(hasta));
                ps.setLong(3, s.getId());
                List<Fila> filas = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) filas.add(leerFila(rs));
                }
                return filas;
            }
        }
    }

    private static Fila leerFila(ResultSet rs) throws SQLException {
        Timestamp creado = rs.getTimestamp("creado_en");
        return new Fila(rs.getLong("id"), rs.getLong("sucursal_id"), rs.getString("estado"),
                creado == null ? null : creado.toLocalDateTime());
    }

    /** Mensaje para el usuario sin host ni credenciales; el detalle queda en el log. */
    static String categoriaError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            // pgjdbc informa el connectTimeout como «Connection attempt timed out.» (08001), sin SocketTimeoutException.
            if (t instanceof SocketTimeoutException
                    || (t.getMessage() != null && t.getMessage().contains("timed out"))) {
                return "Sin respuesta (timeout)";
            }
        }
        String estadoSql = e instanceof SQLException ? ((SQLException) e).getSQLState() : null;
        if (estadoSql != null) {
            if (estadoSql.startsWith("28")) return "Autenticación rechazada";
            if (estadoSql.startsWith("08")) return "Conexión rechazada";
            if (estadoSql.equals("57014")) return "La consulta tardó demasiado";
        }
        return "Error de consulta";
    }

    private static ConvenioSucursalIntegridad sinFilial(Sucursal s, List<Fila> filasCentral, EstadoVentaCredito estado,
                                                        String estadoIntegridad, String error) {
        ConvenioSucursalIntegridad r = comparar(s.getId(), s.getNombre(), filasCentral, null, estado);
        r.setEstadoIntegridad(estadoIntegridad);
        r.setError(error);
        return r;
    }

    /**
     * Compara las filas de una sucursal. La clave es (id, sucursal_id): los ids se repiten entre
     * sucursales y una filial puede tener filas de otras. El filtro de estado solo cambia los conteos;
     * la comparación usa todos los estados, porque un ABIERTO en la filial y CANCELADO en el central
     * filtrado por ABIERTO parecería un faltante.
     *
     * @param filial null si la filial no se pudo consultar.
     */
    static ConvenioSucursalIntegridad comparar(Long sucursalId, String nombre, List<Fila> central,
                                               List<Fila> filial, EstadoVentaCredito estado) {
        Map<Long, Fila> c = porId(central, sucursalId);
        ConvenioSucursalIntegridad r = new ConvenioSucursalIntegridad();
        r.setSucursalId(sucursalId);
        r.setSucursalNombre(nombre);
        r.setCentral(conteo(c.values(), estado));
        r.setUltimoCentral(ultimo(c.values()));
        if (filial == null) return r;

        Map<Long, Fila> f = porId(filial, sucursalId);
        r.setFilial(conteo(f.values(), estado));
        r.setUltimoFilial(ultimo(f.values()));

        List<Long> faltan = new ArrayList<>();
        List<Long> distinto = new ArrayList<>();
        for (Fila fila : f.values()) {
            Fila enCentral = c.get(fila.id);
            if (enCentral == null) faltan.add(fila.id);
            else if (!Objects.equals(enCentral.estado, fila.estado)) distinto.add(fila.id);
        }
        List<Long> sobran = c.keySet().stream().filter(id -> !f.containsKey(id)).sorted().collect(Collectors.toList());
        Collections.sort(faltan);
        Collections.sort(distinto);

        r.setFaltanEnCentral(faltan.size());
        r.setSobranEnCentral(sobran.size());
        r.setEstadoDistinto(distinto.size());
        r.setIdsFaltanEnCentral(tope(faltan));
        r.setIdsSobranEnCentral(tope(sobran));
        r.setIdsEstadoDistinto(tope(distinto));
        r.setEstadoIntegridad(faltan.isEmpty() && sobran.isEmpty() && distinto.isEmpty()
                ? ConvenioSucursalIntegridad.OK : ConvenioSucursalIntegridad.DIFERENCIA);
        return r;
    }

    private static Map<Long, Fila> porId(List<Fila> filas, Long sucursalId) {
        Map<Long, Fila> m = new HashMap<>();
        if (filas == null) return m;
        for (Fila f : filas) {
            if (f.sucursalId == sucursalId) m.put(f.id, f);
        }
        return m;
    }

    private static ConvenioConteo conteo(Collection<Fila> filas, EstadoVentaCredito estado) {
        ConvenioConteo k = new ConvenioConteo();
        for (Fila f : filas) {
            if (estado != null && !estado.name().equals(f.estado)) continue;
            k.setTotal(k.getTotal() + 1);
            if ("ABIERTO".equals(f.estado)) k.setAbiertas(k.getAbiertas() + 1);
            else if ("FINALIZADO".equals(f.estado)) k.setFinalizadas(k.getFinalizadas() + 1);
            else if ("CANCELADO".equals(f.estado)) k.setCanceladas(k.getCanceladas() + 1);
            else k.setOtras(k.getOtras() + 1);
        }
        return k;
    }

    private static String ultimo(Collection<Fila> filas) {
        return filas.stream().map(f -> f.creadoEn).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).map(FORMATO_FECHA_HORA::format).orElse(null);
    }

    private static List<Long> tope(List<Long> ids) {
        return new ArrayList<>(ids.subList(0, Math.min(ids.size(), TOPE_IDS)));
    }
}
