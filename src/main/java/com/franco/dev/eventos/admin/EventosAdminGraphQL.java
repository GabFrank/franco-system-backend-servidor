package com.franco.dev.eventos.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.eventos.CatalogoEventos;
import com.franco.dev.eventos.EventosProperties;
import com.franco.dev.eventos.Suscripcion;
import com.franco.dev.eventos.ValidadorSuscripcion;
import com.franco.dev.eventos.admin.EventosAdminDtos.*;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Administracion de consumidores y suscripciones de eventos por webhook (srv #587, fase C1). Solo el
 * central escribe configuraciones.webhook_suscripcion; las filiales la reciben por replica (MAIN_TO_ALL,
 * apagada hasta la operacion 1c). Rol WEBHOOKS GESTIONAR o ADMIN.
 */
@Component
public class EventosAdminGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    public static final String WEBHOOKS_GESTIONAR = "WEBHOOKS GESTIONAR";

    private final JdbcTemplate jdbc;
    private final TesoreriaSecurityService seg;
    private final EventosProperties props;
    private final ObjectMapper mapper;
    private final VerificadorEndpoint verificador;

    public EventosAdminGraphQL(JdbcTemplate jdbc, TesoreriaSecurityService seg, EventosProperties props,
                               ObjectMapper mapper, VerificadorEndpoint verificador) {
        this.jdbc = jdbc;
        this.seg = seg;
        this.props = props;
        this.mapper = mapper;
        this.verificador = verificador;
    }

    private void requireGestionar() {
        seg.requireAnyRole(WEBHOOKS_GESTIONAR, TesoreriaSecurityService.ADMIN);
    }

    // ------------------------------------------------------------------ catalogo

    public List<EventoCatalogoTipo> eventosCatalogo() {
        requireGestionar();
        List<EventoCatalogoTipo> l = new ArrayList<>();
        for (CatalogoEventos.Tipo t : CatalogoEventos.todos().values()) {
            EventoCatalogoTipo d = new EventoCatalogoTipo();
            d.setTipo(t.nombre);
            d.setVersion(1);
            d.setFamilia(t.familia.name());
            d.setNodo(CatalogoEventos.nodo(t.nombre));
            d.setAtributos(new ArrayList<>(t.atributos));
            List<EventoCatalogoCampo> campos = new ArrayList<>();
            for (Map.Entry<String, CatalogoEventos.Clase> r : t.rutas.entrySet()) {
                EventoCatalogoCampo c = new EventoCatalogoCampo();
                c.setRuta(r.getKey());
                c.setClase(r.getValue().name());
                campos.add(c);
            }
            d.setCampos(campos);
            l.add(d);
        }
        return l;
    }

    // ------------------------------------------------------------------ consumidores

    public List<EventosConsumidor> eventosConsumidores() {
        requireGestionar();
        return jdbc.query("SELECT * FROM configuraciones.eventos_consumidor ORDER BY id", (rs, i) -> consumidor(rs));
    }

    @Transactional
    public EventosConsumidor saveEventosConsumidor(EventosConsumidorInput in) {
        requireGestionar();
        if (in.getNombre() == null || in.getNombre().trim().isEmpty()) throw new GraphQLException("Falta el nombre del consumidor");
        Long usuario = usuarioId();
        Long id;
        if (in.getId() == null) {
            id = jdbc.queryForObject("INSERT INTO configuraciones.eventos_consumidor (nombre, contacto, permite_personales,"
                            + " permite_sensibles, max_suscripciones, activo, creado_por) VALUES (?,?,?,?,?,?,?) RETURNING id", Long.class,
                    in.getNombre().trim(), in.getContacto(), bool(in.getPermitePersonales(), false), bool(in.getPermiteSensibles(), false),
                    in.getMaxSuscripciones() != null ? in.getMaxSuscripciones() : 10, bool(in.getActivo(), true), usuario);
        } else {
            int n = jdbc.update("UPDATE configuraciones.eventos_consumidor SET nombre=?, contacto=?, permite_personales=?,"
                            + " permite_sensibles=?, max_suscripciones=COALESCE(?, max_suscripciones), activo=COALESCE(?, activo),"
                            + " actualizado_en=now() WHERE id=?",
                    in.getNombre().trim(), in.getContacto(), bool(in.getPermitePersonales(), false), bool(in.getPermiteSensibles(), false),
                    in.getMaxSuscripciones(), in.getActivo(), in.getId());
            if (n == 0) throw new GraphQLException("No existe el consumidor " + in.getId());
            id = in.getId();
            // Sin el permiso, las suscripciones del consumidor dejan de recibir lo personal o sensible
            if (!bool(in.getPermitePersonales(), false)) {
                jdbc.update("UPDATE configuraciones.webhook_suscripcion SET datos_personales=false WHERE consumidor_id=? AND datos_personales", id);
            }
            if (!bool(in.getPermiteSensibles(), false)) {
                jdbc.update("UPDATE configuraciones.webhook_suscripcion SET datos_sensibles=false WHERE consumidor_id=? AND datos_sensibles", id);
            }
            if (Boolean.FALSE.equals(in.getActivo())) {
                jdbc.update("UPDATE configuraciones.webhook_suscripcion SET activo=false WHERE consumidor_id=? AND activo", id);
            }
        }
        return jdbc.queryForObject("SELECT * FROM configuraciones.eventos_consumidor WHERE id=?", (rs, i) -> consumidor(rs), id);
    }

    // ------------------------------------------------------------------ suscripciones

    public List<WebhookSuscripcion> webhookSuscripciones(Long consumidorId) {
        requireGestionar();
        if (consumidorId != null) {
            return jdbc.query("SELECT * FROM configuraciones.webhook_suscripcion WHERE consumidor_id=? ORDER BY id",
                    (rs, i) -> suscripcion(rs), consumidorId);
        }
        return jdbc.query("SELECT * FROM configuraciones.webhook_suscripcion ORDER BY id", (rs, i) -> suscripcion(rs));
    }

    public WebhookSuscripcion webhookSuscripcion(Long id) {
        requireGestionar();
        List<WebhookSuscripcion> l = jdbc.query("SELECT * FROM configuraciones.webhook_suscripcion WHERE id=?", (rs, i) -> suscripcion(rs), id);
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Alta o cambio. Valida contra el catalogo y los permisos del consumidor. Un cambio de URL obliga a
     * verificar de nuevo el endpoint: queda verificada=false y activo=false.
     */
    @Transactional
    public WebhookSuscripcion saveWebhookSuscripcion(WebhookSuscripcionInput in) {
        requireGestionar();
        Map<String, Object> cons = consumidorFila(in.getConsumidorId());
        if (!Boolean.TRUE.equals(cons.get("activo"))) throw new GraphQLException("El consumidor esta inactivo");
        Suscripcion s = new Suscripcion();
        s.setUrl(in.getUrl() != null ? in.getUrl().trim() : null);
        s.setEventos(in.getEventos() != null ? in.getEventos() : Collections.emptyList());
        s.setEntrega(in.getEntrega());
        s.setDatosPersonales(bool(in.getDatosPersonales(), false));
        s.setDatosSensibles(bool(in.getDatosSensibles(), false));
        List<String> errores = new ArrayList<>();
        try {
            if (in.getFiltros() != null && !in.getFiltros().trim().isEmpty()) {
                s.setFiltros(mapper.readValue(in.getFiltros(), new TypeReference<Map<String, Map<String, List<Object>>>>() {}));
            }
        } catch (Exception e) {
            errores.add("filtros no es un JSON valido de la forma {\"tipo\": {\"atributo\": [valores]}}");
        }
        try {
            if (in.getCampos() != null && !in.getCampos().trim().isEmpty()) {
                s.setCampos(mapper.readValue(in.getCampos(), new TypeReference<Map<String, List<String>>>() {}));
            }
        } catch (Exception e) {
            errores.add("campos no es un JSON valido de la forma {\"tipo\": [\"ruta\", ...]}");
        }
        try {
            if (in.getUmbrales() != null && !in.getUmbrales().trim().isEmpty()) {
                s.setUmbrales(mapper.readValue(in.getUmbrales(), new TypeReference<Map<String, Map<String, Number>>>() {}));
            }
        } catch (Exception e) {
            errores.add("umbrales no es un JSON valido de la forma {\"tipo\": {\"campo\": minimo}}");
        }
        ValidadorSuscripcion.Permisos p = new ValidadorSuscripcion.Permisos(
                Boolean.TRUE.equals(cons.get("permite_personales")), Boolean.TRUE.equals(cons.get("permite_sensibles")));
        errores.addAll(ValidadorSuscripcion.validar(s, p, props.getHostsPermitidos()));
        if (in.getNombre() == null || in.getNombre().trim().isEmpty()) errores.add("Falta el nombre");
        if (!errores.isEmpty()) throw new GraphQLException("Suscripcion invalida: " + String.join("; ", errores));

        Long id;
        if (in.getId() == null) {
            Integer usadas = jdbc.queryForObject("SELECT count(*) FROM configuraciones.webhook_suscripcion WHERE consumidor_id=? AND activo",
                    Integer.class, in.getConsumidorId());
            Number max = (Number) cons.get("max_suscripciones");
            if (max != null && usadas != null && usadas >= max.intValue()) {
                throw new GraphQLException("El consumidor llego a su tope de " + max + " suscripciones activas");
            }
            id = jdbc.queryForObject("INSERT INTO configuraciones.webhook_suscripcion (consumidor_id, nombre, url, eventos, sucursal_ids,"
                            + " filtros, campos, umbrales, entrega, datos_personales, datos_sensibles, cabecera_nombre, secreto_ref, latido,"
                            + " verificada, activo, config_version, creado_por, creado_en)"
                            + " VALUES (?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,false,false,1,?,now()) RETURNING id",
                    Long.class, in.getConsumidorId(), in.getNombre().trim(), s.getUrl(), textArray(s.getEventos()), bigintArray(in.getSucursalIds()),
                    vacioANull(in.getFiltros()), vacioANull(in.getCampos()), vacioANull(in.getUmbrales()), in.getEntrega(),
                    s.isDatosPersonales(), s.isDatosSensibles(), in.getCabeceraNombre(), in.getSecretoRef(), bool(in.getLatido(), false), usuarioId());
        } else {
            List<String> urls = jdbc.queryForList("SELECT url FROM configuraciones.webhook_suscripcion WHERE id=? AND consumidor_id=? FOR UPDATE",
                    String.class, in.getId(), in.getConsumidorId());
            if (urls.isEmpty()) throw new GraphQLException("No existe la suscripcion " + in.getId() + " de ese consumidor");
            boolean otraUrl = urls.get(0) == null || !urls.get(0).equals(s.getUrl());
            jdbc.update("UPDATE configuraciones.webhook_suscripcion SET nombre=?, url=?, eventos=?, sucursal_ids=?, filtros=CAST(? AS jsonb),"
                            + " campos=CAST(? AS jsonb), umbrales=CAST(? AS jsonb), entrega=?, datos_personales=?, datos_sensibles=?,"
                            + " cabecera_nombre=?, secreto_ref=?, latido=?"
                            + (otraUrl ? ", verificada=false, activo=false" : "") + " WHERE id=?",
                    in.getNombre().trim(), s.getUrl(), textArray(s.getEventos()), bigintArray(in.getSucursalIds()),
                    vacioANull(in.getFiltros()), vacioANull(in.getCampos()), vacioANull(in.getUmbrales()), in.getEntrega(),
                    s.isDatosPersonales(), s.isDatosSensibles(), in.getCabeceraNombre(), in.getSecretoRef(), bool(in.getLatido(), false), in.getId());
            id = in.getId();
        }
        return webhookSuscripcion(id);
    }

    /** Solo una suscripcion verificada se puede activar (tambien lo exige un CHECK de la tabla). */
    @Transactional
    public WebhookSuscripcion activarWebhookSuscripcion(Long id, Boolean activo) {
        requireGestionar();
        WebhookSuscripcion s = webhookSuscripcion(id);
        if (s == null) throw new GraphQLException("No existe la suscripcion " + id);
        if (Boolean.TRUE.equals(activo) && !Boolean.TRUE.equals(s.getVerificada())) {
            throw new GraphQLException("Primero hay que verificar el endpoint de la suscripcion");
        }
        jdbc.update("UPDATE configuraciones.webhook_suscripcion SET activo=? WHERE id=?", Boolean.TRUE.equals(activo), id);
        return webhookSuscripcion(id);
    }

    @Transactional
    public WebhookSuscripcion pausarEventosWebhookSuscripcion(Long id, List<String> eventos) {
        requireGestionar();
        for (String e : eventos) {
            if (CatalogoEventos.tipo(e) == null && !"nodo.latido".equals(e)) throw new GraphQLException("Evento desconocido: " + e);
        }
        int n = jdbc.update("UPDATE configuraciones.webhook_suscripcion SET eventos_pausados=? WHERE id=?", textArray(eventos), id);
        if (n == 0) throw new GraphQLException("No existe la suscripcion " + id);
        return webhookSuscripcion(id);
    }

    /** Borrado logico: queda inactiva (las entregas pendientes pasan a CANCELADA al intentar enviarlas). */
    @Transactional
    public Boolean deleteWebhookSuscripcion(Long id) {
        requireGestionar();
        return jdbc.update("UPDATE configuraciones.webhook_suscripcion SET activo=false, verificada=false WHERE id=?", id) > 0;
    }

    /**
     * Manda py.frc.webhook.verificacion con un codigo; el receptor tiene que devolverlo en el cuerpo. Asi
     * nadie registra una URL ajena. Solo con eso queda verificada=true.
     */
    public WebhookVerificacion verificarWebhookSuscripcion(Long id) {
        requireGestionar();
        WebhookSuscripcion s = webhookSuscripcion(id);
        if (s == null) throw new GraphQLException("No existe la suscripcion " + id);
        VerificadorEndpoint.Resultado r = verificador.verificar(s.getId(), s.getUrl(), s.getCabeceraNombre(), s.getSecretoRef());
        if (r.ok) {
            jdbc.update("UPDATE configuraciones.webhook_suscripcion SET verificada=true WHERE id=? AND url=?", id, s.getUrl());
        }
        WebhookVerificacion v = new WebhookVerificacion();
        v.setVerificada(r.ok);
        v.setStatusHttp(r.status);
        v.setMensaje(r.mensaje);
        return v;
    }

    // ------------------------------------------------------------------ utilidades

    private Map<String, Object> consumidorFila(Long id) {
        if (id == null) throw new GraphQLException("Falta el consumidor");
        List<Map<String, Object>> l = jdbc.queryForList("SELECT * FROM configuraciones.eventos_consumidor WHERE id=?", id);
        if (l.isEmpty()) throw new GraphQLException("No existe el consumidor " + id);
        return l.get(0);
    }

    private Long usuarioId() {
        try {
            return seg.currentUsuario() != null ? seg.currentUsuario().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean bool(Boolean b, boolean def) {
        return b != null ? b : def;
    }

    private static String vacioANull(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }

    private Array textArray(List<String> l) {
        if (l == null) return null;
        return jdbc.execute((java.sql.Connection c) -> c.createArrayOf("text", l.toArray()));
    }

    private Array bigintArray(List<Long> l) {
        if (l == null || l.isEmpty()) return null;
        return jdbc.execute((java.sql.Connection c) -> c.createArrayOf("bigint", l.toArray()));
    }

    private static List<String> textos(Array a) throws SQLException {
        if (a == null) return Collections.emptyList();
        return Arrays.stream((Object[]) a.getArray()).map(String::valueOf).collect(Collectors.toList());
    }

    private static EventosConsumidor consumidor(ResultSet rs) throws SQLException {
        EventosConsumidor c = new EventosConsumidor();
        c.setId(rs.getLong("id"));
        c.setNombre(rs.getString("nombre"));
        c.setContacto(rs.getString("contacto"));
        c.setPermitePersonales(rs.getBoolean("permite_personales"));
        c.setPermiteSensibles(rs.getBoolean("permite_sensibles"));
        c.setMaxSuscripciones(rs.getInt("max_suscripciones"));
        c.setActivo(rs.getBoolean("activo"));
        c.setCreadoEn(rs.getString("creado_en"));
        return c;
    }

    private static WebhookSuscripcion suscripcion(ResultSet rs) throws SQLException {
        WebhookSuscripcion s = new WebhookSuscripcion();
        s.setId(rs.getLong("id"));
        long cons = rs.getLong("consumidor_id");
        s.setConsumidorId(rs.wasNull() ? null : cons);
        s.setNombre(rs.getString("nombre"));
        s.setUrl(rs.getString("url"));
        s.setEventos(textos(rs.getArray("eventos")));
        s.setEventosPausados(textos(rs.getArray("eventos_pausados")));
        Array suc = rs.getArray("sucursal_ids");
        s.setSucursalIds(suc == null ? null : Arrays.stream((Object[]) suc.getArray())
                .map(o -> ((Number) o).longValue()).collect(Collectors.toList()));
        s.setFiltros(rs.getString("filtros"));
        s.setCampos(rs.getString("campos"));
        s.setUmbrales(rs.getString("umbrales"));
        s.setEntrega(rs.getString("entrega"));
        s.setDatosPersonales(rs.getBoolean("datos_personales"));
        s.setDatosSensibles(rs.getBoolean("datos_sensibles"));
        s.setCabeceraNombre(rs.getString("cabecera_nombre"));
        s.setSecretoRef(rs.getString("secreto_ref"));
        s.setLatido(rs.getBoolean("latido"));
        s.setVerificada(rs.getBoolean("verificada"));
        s.setActivo(rs.getBoolean("activo"));
        s.setConfigVersion(rs.getLong("config_version"));
        s.setCreadoEn(rs.getString("creado_en"));
        s.setActualizadoEn(rs.getString("actualizado_en"));
        return s;
    }
}
