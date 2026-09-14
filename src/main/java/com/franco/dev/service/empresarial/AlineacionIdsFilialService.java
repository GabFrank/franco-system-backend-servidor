package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pone la secuencia de ids de cada filial por encima de lo que el central ya tiene.
 *
 * El central genera ids impares y los filiales pares (ver IdCentral y la migracion
 * V223.1). La paridad evita choques con todo lo que se genere de ahora en mas,
 * pero antes de este esquema el central genero ids pares para filas de sucursales
 * de filiales: si un filial llegara a generar uno de esos, su INSERT replicado
 * chocaria igual y cortaria la replicacion. Se corre una vez, despues de desplegar
 * las migraciones de los dos lados; correrla de nuevo no hace dano.
 */
@Service
public class AlineacionIdsFilialService {

    private static final Logger logger = LoggerFactory.getLogger(AlineacionIdsFilialService.class);

    /**
     * Tablas que el filial replica al central y en las que el central tambien inserta.
     */
    static final List<Tabla> TABLAS = Arrays.asList(
            new Tabla("configuraciones.inicio_sesion", true),
            new Tabla("financiero.gasto", true),
            new Tabla("financiero.venta_tarjeta", true),
            new Tabla("financiero.maletin", false),
            new Tabla("financiero.movimiento_personas", false));

    private final JdbcTemplate jdbcTemplate;
    private final SucursalService sucursalService;
    private final LogicalReplicationService replicationService;

    public AlineacionIdsFilialService(JdbcTemplate jdbcTemplate, SucursalService sucursalService,
                                      LogicalReplicationService replicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sucursalService = sucursalService;
        this.replicationService = replicationService;
    }

    /**
     * Alinea todos los filiales activos. Un filial que no responde o que todavia no
     * tiene la migracion no frena a los demas: queda anotado con su error.
     */
    public Resultado alinearTodas() {
        List<Sucursal> sucursales = sucursalService.findAllExcludingServer().stream()
                .filter(s -> Boolean.TRUE.equals(s.getActivo()) && s.getIp() != null && s.getPuerto() != null)
                .filter(s -> s.getId() != null && s.getId() != 0)
                .collect(Collectors.toList());

        List<String> lineas = new ArrayList<>();
        boolean todasOk = true;
        for (Sucursal s : sucursales) {
            String nombre = s.getNombre() + " (" + s.getId() + ")";
            try {
                lineas.add(nombre + ": " + alinear(s.getId()));
            } catch (Exception e) {
                todasOk = false;
                logger.warn("Alineacion de ids para sucursal {} fallo: {}", s.getId(), e.getMessage());
                lineas.add(nombre + ": ERROR " + e.getMessage());
            }
        }
        return new Resultado(todasOk, lineas);
    }

    String alinear(Long sucursalId) {
        List<String> partes = new ArrayList<>();
        for (Tabla tabla : TABLAS) {
            Long mayorPar = mayorIdParDelCentral(tabla, sucursalId);
            Long proximo = replicationService.queryRemoteForObject(sucursalId,
                    "SELECT configuraciones.alinear_secuencia_par(?::regclass, ?::regclass, ?)",
                    Long.class, tabla.secuencia(), tabla.nombre, mayorPar == null ? 0L : mayorPar);
            partes.add(tabla.nombre + " -> " + proximo);
        }
        return String.join(", ", partes);
    }

    /**
     * Solo importan los pares: un impar lo genero el central y el filial nunca lo va a generar.
     */
    Long mayorIdParDelCentral(Tabla tabla, Long sucursalId) {
        if (tabla.porSucursal) {
            return jdbcTemplate.queryForObject(
                    "SELECT MAX(id) FROM " + tabla.nombre + " WHERE id % 2 = 0 AND sucursal_id = ?",
                    Long.class, sucursalId);
        }
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM " + tabla.nombre + " WHERE id % 2 = 0", Long.class);
    }

    static class Tabla {
        final String nombre;
        /**
         * La clave del central incluye sucursal_id: el mayor par se busca solo
         * entre las filas de esa sucursal. Si no, el id es unico en toda la tabla.
         */
        final boolean porSucursal;

        Tabla(String nombre, boolean porSucursal) {
            this.nombre = nombre;
            this.porSucursal = porSucursal;
        }

        String secuencia() {
            return nombre + "_id_seq";
        }
    }

    public static class Resultado {
        private final boolean success;
        private final List<String> lineas;

        Resultado(boolean success, List<String> lineas) {
            this.success = success;
            this.lineas = lineas;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMensaje() {
            return lineas.isEmpty() ? "No hay filiales activos con IP y puerto" : String.join("\n", lineas);
        }
    }
}
