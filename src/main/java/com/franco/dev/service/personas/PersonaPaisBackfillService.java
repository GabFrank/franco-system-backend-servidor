package com.franco.dev.service.personas;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.empresarial.LogicalReplicationService;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sube al central el pais que las cajas cargaron en cada filial antes de que el central tuviera la columna (#666).
 * Lee cada filial en solo lectura (como ConvenioIntegridadService, con timeouts mas largos: la tailnet tiene
 * ~230 ms de RTT) y graba en el central solo donde no hay pais. Si dos filiales dicen paises distintos, no graba
 * y lo informa. Idempotente: se corre de nuevo cuando vuelven las filiales que no respondieron.
 */
@Service
public class PersonaPaisBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PersonaPaisBackfillService.class);
    private static final Pattern HOST_VALIDO = Pattern.compile("^[A-Za-z0-9.\\-]+$");
    private static final Pattern BASE_VALIDA = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final long ESPERA_TOTAL_SEG = 90;
    private static final String SQL = "SELECT id, pais_id FROM personas.persona WHERE pais_id IS NOT NULL";

    public static final String OK = "OK";
    public static final String SIN_IP = "SIN_IP";
    public static final String SIN_CONEXION = "SIN_CONEXION";
    public static final String SIN_COLUMNA = "SIN_COLUMNA";

    private final SucursalService sucursalService;
    private final LogicalReplicationService replicationService;
    private final PersonaService personaService;
    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService pool = Executors.newFixedThreadPool(24, r -> {
        Thread t = new Thread(r, "backfill-pais");
        t.setDaemon(true);
        return t;
    });
    private final ReentrantLock enCurso = new ReentrantLock();

    @Value("${spring.datasource.username:franco}")
    private String dbUsername;

    @Value("${spring.datasource.password:franco}")
    private String dbPassword;

    public PersonaPaisBackfillService(SucursalService sucursalService, LogicalReplicationService replicationService,
                                      PersonaService personaService, JdbcTemplate jdbcTemplate) {
        this.sucursalService = sucursalService;
        this.replicationService = replicationService;
        this.personaService = personaService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PreDestroy
    void cerrar() {
        pool.shutdownNow();
    }

    @Data
    public static class Filial {
        private Long sucursalId;
        private String nombre;
        private String estado;
        private Integer personasConPais;
        private String error;
    }

    @Data
    public static class Resultado {
        private int grabados;
        private int sinCambios;
        private List<Long> idsGrabados = new ArrayList<>();
        private List<Long> conflictos = new ArrayList<>();
        private List<Long> paisesDesconocidos = new ArrayList<>();
        private List<Filial> filiales = new ArrayList<>();
        /** true solo si respondieron todas las filiales: si no, hay que volver a correrlo. */
        private boolean completo;
    }

    /** Donde conectarse a una filial: sucursal.ip o, si no, el host de la suscripcion del central. */
    static final class Destino {
        final String host;
        final int puerto;
        final String base;

        Destino(String host, int puerto, String base) {
            this.host = host;
            this.puerto = puerto;
            this.base = base;
        }
    }

    public Resultado ejecutar() {
        if (!enCurso.tryLock()) throw new GraphQLException("Ya hay un backfill de paises en curso.");
        Map<Sucursal, Future<Map<Long, Long>>> futuros = new LinkedHashMap<>();
        try {
            String base = replicationService.getBranchDbName();
            if (base == null || !BASE_VALIDA.matcher(base).matches())
                throw new GraphQLException("El nombre de la base de las filiales configurado no es valido.");
            Resultado res = new Resultado();
            for (Sucursal s : sucursalService.findAllExcludingServer()) {
                List<Destino> destinos = destinos(s, base);
                if (destinos.isEmpty()) {
                    res.filiales.add(filial(s, SIN_IP, null, "La sucursal no tiene IP ni suscripcion"));
                    continue;
                }
                futuros.put(s, pool.submit(() -> leerConAlternativa(destinos)));
            }

            // personaId -> paises que informan las filiales
            Map<Long, Set<Long>> paises = new TreeMap<>();
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_TOTAL_SEG);
            for (Map.Entry<Sucursal, Future<Map<Long, Long>>> e : futuros.entrySet()) {
                Sucursal s = e.getKey();
                try {
                    Map<Long, Long> filas = e.getValue().get(Math.max(0, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
                    filas.forEach((persona, pais) -> paises.computeIfAbsent(persona, k -> new TreeSet<>()).add(pais));
                    res.filiales.add(filial(s, OK, filas.size(), null));
                } catch (TimeoutException ex) {
                    e.getValue().cancel(true);
                    res.filiales.add(filial(s, SIN_CONEXION, null, "Sin respuesta (timeout)"));
                } catch (ExecutionException ex) {
                    log.warn("Backfill pais: no se pudo leer la filial {}: {}", s.getId(), ex.getCause().toString());
                    boolean sinColumna = ex.getCause() instanceof SQLException
                            && "42703".equals(((SQLException) ex.getCause()).getSQLState());
                    res.filiales.add(filial(s, sinColumna ? SIN_COLUMNA : SIN_CONEXION, null,
                            sinColumna ? "La filial no tiene personas.persona.pais_id" : "No se pudo consultar"));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new GraphQLException("Backfill interrumpido.");
                }
            }
            grabar(paises, res);
            res.completo = res.filiales.stream().allMatch(f -> OK.equals(f.estado));
            log.info("Backfill pais: grabados={} sinCambios={} conflictos={} desconocidos={} completo={} ids={}",
                    res.grabados, res.sinCambios, res.conflictos, res.paisesDesconocidos, res.completo, res.idsGrabados);
            return res;
        } finally {
            futuros.values().forEach(f -> f.cancel(true));
            enCurso.unlock();
        }
    }

    /** Graba fila por fila: un pais huerfano (FK) o una persona que no esta no cortan la corrida. */
    void grabar(Map<Long, Set<Long>> paises, Resultado res) {
        paises.forEach((persona, ps) -> {
            if (ps.size() > 1) {
                res.conflictos.add(persona);
                return;
            }
            try {
                if (personaService.asignarPaisSiNulo(persona, ps.iterator().next())) {
                    res.grabados++;
                    res.idsGrabados.add(persona);
                } else {
                    res.sinCambios++;
                }
            } catch (DataIntegrityViolationException fk) {
                res.paisesDesconocidos.add(persona);
            }
        });
    }

    List<Destino> destinos(Sucursal s, String base) {
        List<Destino> d = new ArrayList<>();
        String ip = s.getIp() == null ? null : s.getIp().trim();
        if (ip != null && HOST_VALIDO.matcher(ip).matches() && s.getPuerto() != null
                && s.getPuerto() > 0 && s.getPuerto() <= 65535) {
            d.add(new Destino(ip, s.getPuerto(), base));
        }
        try {
            // sucursal.ip puede estar vieja aunque la replica ande: las suscripciones se movieron a la tailnet.
            List<String> conninfo = jdbcTemplate.queryForList(
                    "SELECT subconninfo FROM pg_subscription WHERE subname = ?", String.class,
                    replicationService.generateBranchSubscriptionName(s.getId()));
            Destino sub = conninfo.isEmpty() ? null : desdeConninfo(conninfo.get(0));
            if (sub != null && (d.isEmpty() || !d.get(0).host.equals(sub.host) || d.get(0).puerto != sub.puerto))
                d.add(sub);
        } catch (Exception e) {
            log.debug("Backfill pais: sin conninfo para la filial {}: {}", s.getId(), e.toString());
        }
        return d;
    }

    static Destino desdeConninfo(String conninfo) {
        String host = valor(conninfo, "host");
        String puerto = valor(conninfo, "port");
        String base = valor(conninfo, "dbname");
        if (host == null || !HOST_VALIDO.matcher(host).matches() || base == null || !BASE_VALIDA.matcher(base).matches())
            return null;
        try {
            return new Destino(host, puerto == null ? 5432 : Integer.parseInt(puerto), base);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String valor(String conninfo, String clave) {
        Matcher m = Pattern.compile("(?:^|\\s)" + clave + "=(\\S+)").matcher(conninfo);
        return m.find() ? m.group(1).replace("'", "") : null;
    }

    private Map<Long, Long> leerConAlternativa(List<Destino> destinos) throws SQLException {
        SQLException ultimo = null;
        for (Destino d : destinos) {
            try {
                return leer(d);
            } catch (SQLException e) {
                if ("42703".equals(e.getSQLState())) throw e; // conecto: la filial no tiene la columna
                ultimo = e;
            }
        }
        throw ultimo;
    }

    /** personaId -> paisId de una filial, en solo lectura. */
    Map<Long, Long> leer(Destino d) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        props.setProperty("connectTimeout", "8");
        props.setProperty("loginTimeout", "8");
        props.setProperty("socketTimeout", "40");
        props.setProperty("ApplicationName", "frc-central-backfill-pais");
        props.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=30000");
        String url = String.format("jdbc:postgresql://%s:%d/%s", d.host, d.puerto, d.base);
        try (Connection con = DriverManager.getConnection(url, props);
             PreparedStatement ps = con.prepareStatement(SQL)) {
            con.setReadOnly(true);
            Map<Long, Long> filas = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) filas.put(rs.getLong(1), rs.getLong(2));
            }
            return filas;
        }
    }

    private static Filial filial(Sucursal s, String estado, Integer personas, String error) {
        Filial f = new Filial();
        f.sucursalId = s.getId();
        f.nombre = s.getNombre();
        f.estado = estado;
        f.personasConPais = personas;
        f.error = error;
        return f;
    }
}
