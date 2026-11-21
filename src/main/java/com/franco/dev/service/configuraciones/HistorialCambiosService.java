package com.franco.dev.service.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionDetalle;
import com.franco.dev.domain.configuraciones.ModificacionRegistro;
import com.franco.dev.domain.configuraciones.enums.TipoOperacion;
import com.franco.dev.repository.configuraciones.ModificacionDetalleRepository;
import com.franco.dev.repository.configuraciones.ModificacionRegistroRepository;
import com.franco.dev.utilitarios.DateUtils;
import com.franco.dev.utilitarios.Paginacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * «Historial de cambios»: el registro de modificaciones del sistema contado para el personal administrativo
 * («Cambió el precio de COCA COLA 2L · UNIDAD · CONTADO en Sucursal X: 96.000 → 98.000»), paginado en el central.
 *
 * Los textos se arman al leer, con los nombres de hoy: sirve también para lo registrado antes de este cambio.
 * Por página se hacen pocas consultas: los registros, sus detalles y una búsqueda de nombres por tabla.
 */
@Service
public class HistorialCambiosService {

    private final ModificacionRegistroRepository registroRepository;
    private final ModificacionDetalleRepository detalleRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public HistorialCambiosService(ModificacionRegistroRepository registroRepository,
                                   ModificacionDetalleRepository detalleRepository,
                                   NamedParameterJdbcTemplate jdbc) {
        this.registroRepository = registroRepository;
        this.detalleRepository = detalleRepository;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- DTOs

    public static class HistorialCambio {
        private Long id;
        private LocalDateTime fecha;
        private Long usuarioId;
        private String usuario;
        private String categoria;
        private String accion;
        private String objeto;
        private String observacion;
        private Long productoId;
        private List<HistorialCambioDetalle> cambios = new ArrayList<>();

        public Long getId() { return id; }
        public LocalDateTime getFecha() { return fecha; }
        public Long getUsuarioId() { return usuarioId; }
        public String getUsuario() { return usuario; }
        public String getCategoria() { return categoria; }
        public String getAccion() { return accion; }
        public String getObjeto() { return objeto; }
        public String getObservacion() { return observacion; }
        public Long getProductoId() { return productoId; }
        public List<HistorialCambioDetalle> getCambios() { return cambios; }
        /** Los cambios en una línea para la lista: «Precio: 6.000 → 5.000», o «Precio: 981.818» en un alta. */
        public String getResumen() {
            if (cambios.isEmpty()) return null;
            List<String> partes = new ArrayList<>();
            for (HistorialCambioDetalle c : cambios.subList(0, Math.min(2, cambios.size()))) {
                if (c.antes != null && c.despues != null) partes.add(c.campo + ": " + c.antes + " → " + c.despues);
                else partes.add(c.campo + ": " + (c.despues != null ? c.despues : c.antes));
            }
            String resumen = String.join(" · ", partes);
            return cambios.size() > 2 ? resumen + " (+" + (cambios.size() - 2) + " más)" : resumen;
        }
        /** Una actualización registrada sin decir qué campos cambiaron (pasa con registros viejos). */
        public Boolean getSinDetalle() { return cambios.isEmpty(); }
    }

    public static class HistorialCambioDetalle {
        private final String campo;
        private final String antes;
        private final String despues;

        HistorialCambioDetalle(String campo, String antes, String despues) {
            this.campo = campo;
            this.antes = antes;
            this.despues = despues;
        }

        public String getCampo() { return campo; }
        public String getAntes() { return antes; }
        public String getDespues() { return despues; }
    }

    public static class HistorialCambioCategoria {
        private final String codigo;
        private final String nombre;

        HistorialCambioCategoria(String codigo, String nombre) {
            this.codigo = codigo;
            this.nombre = nombre;
        }

        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
    }

    public List<HistorialCambioCategoria> categorias() {
        return HistorialCambiosFormato.CATEGORIAS.values().stream()
                .map(c -> new HistorialCambioCategoria(c.codigo, c.nombre))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public Page<HistorialCambio> buscar(String fechaInicio, String fechaFin, Long usuarioId, Long productoId,
                                        String categoria, Integer page, Integer size) {
        Pageable pageable = Paginacion.of(page, size,
                Sort.by(Sort.Order.desc("modificadoEn"), Sort.Order.desc("id")));
        Page<ModificacionRegistro> registros = registroRepository.findAll(
                filtros(fechaInicio, fechaFin, usuarioId, productoId, categoria), pageable);
        List<HistorialCambio> contenido = armar(registros.getContent());
        return new PageImpl<>(contenido, pageable, registros.getTotalElements());
    }

    static Specification<ModificacionRegistro> filtros(String fechaInicio, String fechaFin, Long usuarioId,
                                                       Long productoId, String categoria) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.get("activo")));
            LocalDateTime inicio = DateUtils.stringToDate(fechaInicio);
            if (inicio != null) p.add(cb.greaterThanOrEqualTo(root.get("modificadoEn"), inicio));
            LocalDateTime fin = DateUtils.stringToDate(fechaFin);
            if (fin != null) p.add(cb.lessThanOrEqualTo(root.get("modificadoEn"), fin));
            if (usuarioId != null) p.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            if (productoId != null) p.add(cb.equal(root.get("productoId"), productoId));
            HistorialCambiosFormato.Categoria c = categoria != null
                    ? HistorialCambiosFormato.CATEGORIAS.get(categoria) : null;
            if (c != null) p.add(root.get("tipoEntidad").in(c.tiposEntidad));
            // Una actualización que no dejó ningún dato a la vista («Entidad actualizada», o solo el usuario y la
            // fecha) no le dice nada a nadie: suele ir pegada al registro con el cambio real.
            Subquery<Long> conDatos = query.subquery(Long.class);
            Root<ModificacionDetalle> d = conDatos.from(ModificacionDetalle.class);
            conDatos.select(d.get("id")).where(
                    cb.equal(d.get("modificacionRegistro"), root),
                    cb.not(d.get("campoNombre").in(HistorialCambiosFormato.CAMPOS_OCULTOS)));
            p.add(cb.or(cb.notEqual(root.get("tipoOperacion"), TipoOperacion.UPDATE), cb.exists(conDatos)));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    // ---------------------------------------------------------------- armado

    private List<HistorialCambio> armar(List<ModificacionRegistro> registros) {
        if (registros.isEmpty()) return new ArrayList<>();
        List<Long> ids = registros.stream().map(ModificacionRegistro::getId).collect(Collectors.toList());
        Map<Long, List<ModificacionDetalle>> detallesPorRegistro = detalleRepository.findByModificacionRegistroIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(d -> d.getModificacionRegistro().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        Map<Long, Map<String, ModificacionDetalle>> previos = costosAnteriores(registros);

        Nombres nombres = new Nombres();
        previos.values().forEach(m -> m.values().forEach(d -> nombres.pedir(d.getCampoNombre(), d.getValorNuevoId())));
        for (ModificacionRegistro r : registros) {
            nombres.contexto(r.getTipoEntidad(), r.getEntidadId());
            if (r.getProductoId() != null) nombres.pedir("producto", r.getProductoId());
            if (r.getUsuario() != null) nombres.pedir("usuario", r.getUsuario().getId());
            for (ModificacionDetalle d : detallesPorRegistro.getOrDefault(r.getId(), new ArrayList<>())) {
                if (d.getValorAnteriorId() != null) nombres.pedir(d.getCampoNombre(), d.getValorAnteriorId());
                if (d.getValorNuevoId() != null) nombres.pedir(d.getCampoNombre(), d.getValorNuevoId());
            }
        }
        nombres.buscar();

        List<HistorialCambio> resultado = new ArrayList<>();
        for (ModificacionRegistro r : registros) {
            resultado.add(armar(r, detallesPorRegistro.getOrDefault(r.getId(), new ArrayList<>()),
                    previos.get(r.getId()), nombres));
        }
        return resultado;
    }

    /**
     * Un costo nuevo es una fila nueva (no se edita la anterior): su «antes» es el costo que se había registrado
     * antes para el mismo producto y la misma sucursal. Se busca en el historial porque las filas viejas de
     * costo_por_producto no siempre siguen en la base. Devuelve, por registro, los datos del costo anterior.
     */
    private Map<Long, Map<String, ModificacionDetalle>> costosAnteriores(List<ModificacionRegistro> registros) {
        List<Long> ids = registros.stream()
                .filter(r -> "COSTO_POR_PRODUCTO".equals(r.getTipoEntidad()) && r.getTipoOperacion() == TipoOperacion.INSERT
                        && r.getProductoId() != null)
                .map(ModificacionRegistro::getId)
                .collect(Collectors.toList());
        Map<Long, Map<String, ModificacionDetalle>> previos = new HashMap<>();
        if (ids.isEmpty()) return previos;
        String sql = "SELECT r.id AS registro_id, d2.campo_nombre, d2.valor_nuevo, d2.valor_nuevo_id "
                + "FROM configuraciones.modificacion_registro r "
                + "JOIN configuraciones.modificacion_detalle ds ON ds.modificacion_registro_id = r.id AND ds.campo_nombre = 'sucursal' "
                + "JOIN LATERAL (SELECT r2.id FROM configuraciones.modificacion_registro r2 "
                + "  JOIN configuraciones.modificacion_detalle ds2 ON ds2.modificacion_registro_id = r2.id "
                + "   AND ds2.campo_nombre = 'sucursal' AND ds2.valor_nuevo_id = ds.valor_nuevo_id "
                + "  WHERE r2.tipo_entidad = 'COSTO_POR_PRODUCTO' AND r2.producto_id = r.producto_id AND r2.id < r.id "
                + "  ORDER BY r2.id DESC LIMIT 1) prev ON true "
                + "JOIN configuraciones.modificacion_detalle d2 ON d2.modificacion_registro_id = prev.id "
                + "WHERE r.id IN (:ids)";
        try {
            jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
                ModificacionDetalle d = new ModificacionDetalle();
                d.setCampoNombre(rs.getString("campo_nombre"));
                d.setValorNuevo(rs.getString("valor_nuevo"));
                long valorId = rs.getLong("valor_nuevo_id");
                d.setValorNuevoId(rs.wasNull() ? null : valorId);
                previos.computeIfAbsent(rs.getLong("registro_id"), k -> new HashMap<>()).put(d.getCampoNombre(), d);
            });
        } catch (Exception e) {
            // Sin el costo anterior la fila se muestra igual, solo con el valor nuevo.
        }
        return previos;
    }

    private HistorialCambio armar(ModificacionRegistro r, List<ModificacionDetalle> detalles,
                                  Map<String, ModificacionDetalle> previo, Nombres nombres) {
        HistorialCambio h = new HistorialCambio();
        h.id = r.getId();
        h.fecha = r.getModificadoEn();
        h.usuarioId = r.getUsuario() != null ? r.getUsuario().getId() : null;
        h.usuario = h.usuarioId != null ? nombres.de("usuario", h.usuarioId) : null;
        h.categoria = HistorialCambiosFormato.categoriaDe(r.getTipoEntidad());
        h.productoId = r.getProductoId();
        h.observacion = r.getObservacion();

        TipoOperacion op = r.getTipoOperacion();
        boolean baja = op == TipoOperacion.DELETE || op == TipoOperacion.SOFT_DELETE;
        Map<String, String> nuevos = new HashMap<>();
        List<ModificacionDetalle> visibles = detalles.stream()
                .filter(d -> HistorialCambiosFormato.campoVisible(d.getCampoNombre(), r.getTipoEntidad(), op))
                .sorted(Comparator.comparingInt((ModificacionDetalle d) -> HistorialCambiosFormato.ordenCampo(d.getCampoNombre()))
                        .thenComparing(d -> d.getOrden() == null ? 0 : d.getOrden()))
                .collect(Collectors.toList());
        for (ModificacionDetalle d : visibles) {
            String antes = op == TipoOperacion.INSERT ? null : texto(d, d.getValorAnterior(), d.getValorAnteriorId(), nombres);
            ModificacionDetalle anterior = previo != null ? previo.get(d.getCampoNombre()) : null;
            if (anterior != null) antes = texto(d, anterior.getValorNuevo(), anterior.getValorNuevoId(), nombres);
            String despues = baja ? null : texto(d, d.getValorNuevo(), d.getValorNuevoId(), nombres);
            if (HistorialCambiosFormato.campoSecreto(d.getCampoNombre())) {
                antes = antes != null ? "(oculto)" : null;
                despues = despues != null ? "(oculto)" : null;
            }
            // Sin valores o sin cambio real (un alta con el campo vacío, una edición que dejó lo mismo): no aporta.
            if (antes == null && despues == null) continue;
            if (op == TipoOperacion.UPDATE && Objects.equals(antes, despues)) continue;
            // Costo con su costo anterior: solo lo que cambió (la sucursal y la moneda suelen ser las mismas).
            if (previo != null && !"costoMedio".equals(d.getCampoNombre()) && Objects.equals(antes, despues)) continue;
            h.cambios.add(new HistorialCambioDetalle(HistorialCambiosFormato.etiquetaCampo(d.getCampoNombre()), antes, despues));
            if (d.getValorNuevo() != null) nuevos.put(d.getCampoNombre(), d.getValorNuevo().trim());
        }
        h.accion = previo != null && !previo.isEmpty()
                ? "Cambió el costo"
                : HistorialCambiosFormato.accion(r.getTipoEntidad(), op, nuevos);
        h.objeto = objeto(r, detalles, nombres);
        return h;
    }

    /** El valor de un detalle: si apunta a otra tabla, su nombre; si no, con formato. */
    private String texto(ModificacionDetalle d, String crudo, Long id, Nombres nombres) {
        if (crudo == null && id == null) return null;
        if (id != null && (crudo == null || HistorialCambiosFormato.esReferencia(crudo))) {
            String nombre = nombres.de(d.getCampoNombre(), id);
            if (nombre != null) return nombre;
        }
        return HistorialCambiosFormato.valor(d.getCampoNombre(), crudo);
    }

    /** Sobre qué fue el cambio, con los nombres de hoy: «COCA COLA 2L · UNIDAD · CONTADO · CENTRAL». */
    private String objeto(ModificacionRegistro r, List<ModificacionDetalle> detalles, Nombres nombres) {
        String contexto = nombres.contextoDe(r.getTipoEntidad(), r.getEntidadId());
        if (contexto != null && !contexto.isEmpty()) return contexto;
        // La fila ya no existe (se borró): lo que quedó en el detalle.
        if (r.getProductoId() != null) {
            String producto = nombres.de("producto", r.getProductoId());
            if (producto != null) return producto;
        }
        for (ModificacionDetalle d : detalles) {
            if ("descripcion".equals(d.getCampoNombre()) || "codigo".equals(d.getCampoNombre())) {
                String v = d.getValorAnterior() != null ? d.getValorAnterior() : d.getValorNuevo();
                if (v != null) return v;
            }
        }
        return r.getEntidadId() != null ? "N° " + r.getEntidadId() : null;
    }

    // ---------------------------------------------------------------- nombres

    /** Junta los ids de la página y busca sus nombres de a una consulta por tabla. */
    private class Nombres {
        private final Map<String, Set<Long>> pedidos = new HashMap<>();
        private final Map<String, Map<Long, String>> encontrados = new HashMap<>();
        private final Map<String, Set<Long>> contextosPedidos = new HashMap<>();
        private final Map<String, Map<Long, String>> contextos = new HashMap<>();

        void pedir(String campo, Long id) {
            String tabla = tablaDeCampo(campo);
            if (tabla != null && id != null) pedidos.computeIfAbsent(tabla, k -> new HashSet<>()).add(id);
        }

        void contexto(String tipoEntidad, Long entidadId) {
            if (CONTEXTO_SQL.containsKey(tipoEntidad) && entidadId != null) {
                contextosPedidos.computeIfAbsent(tipoEntidad, k -> new HashSet<>()).add(entidadId);
            }
        }

        String de(String campo, Long id) {
            String tabla = tablaDeCampo(campo);
            if (tabla == null || id == null) return null;
            return encontrados.getOrDefault(tabla, new HashMap<>()).get(id);
        }

        String contextoDe(String tipoEntidad, Long entidadId) {
            if (entidadId == null) return null;
            return contextos.getOrDefault(tipoEntidad, new HashMap<>()).get(entidadId);
        }

        void buscar() {
            pedidos.forEach((tabla, ids) -> encontrados.put(tabla, consultar(NOMBRE_SQL.get(tabla), ids)));
            contextosPedidos.forEach((tipo, ids) -> contextos.put(tipo, consultar(CONTEXTO_SQL.get(tipo), ids)));
        }

        private Map<Long, String> consultar(String sql, Collection<Long> ids) {
            Map<Long, String> mapa = new HashMap<>();
            if (sql == null || ids.isEmpty()) return mapa;
            try {
                jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
                    String texto = rs.getString("texto");
                    if (texto != null) mapa.put(rs.getLong("id"), texto.trim());
                });
            } catch (Exception e) {
                // Sin nombres la fila se muestra igual, con el número.
            }
            return mapa;
        }
    }

    /** Qué tabla nombra cada campo de referencia que guarda el registro. */
    private static String tablaDeCampo(String campo) {
        if (campo == null) return null;
        switch (campo) {
            case "producto": return "producto";
            case "presentacion": return "presentacion";
            case "sucursal": return "sucursal";
            case "tipoPrecio": return "tipoPrecio";
            case "moneda": return "moneda";
            case "usuario": return "usuario";
            case "subfamilia": return "subfamilia";
            case "tipoPresentacion": return "tipoPresentacion";
            case "zona": return "zona";
            case "lote": return "lote";
            default: return null;
        }
    }

    private static final Map<String, String> NOMBRE_SQL = new HashMap<>();
    private static final Map<String, String> CONTEXTO_SQL = new HashMap<>();

    private static final String PRESENTACION_TEXTO =
            "concat_ws(' · ', pr.descripcion, coalesce(nullif(p.descripcion, ''), 'x' || trim(to_char(p.cantidad, 'FM999999990.##'))))";

    static {
        NOMBRE_SQL.put("producto", "SELECT id, descripcion AS texto FROM productos.producto WHERE id IN (:ids)");
        NOMBRE_SQL.put("presentacion", "SELECT p.id, " + PRESENTACION_TEXTO + " AS texto FROM productos.presentacion p "
                + "JOIN productos.producto pr ON pr.id = p.producto_id WHERE p.id IN (:ids)");
        NOMBRE_SQL.put("sucursal", "SELECT id, nombre AS texto FROM empresarial.sucursal WHERE id IN (:ids)");
        NOMBRE_SQL.put("tipoPrecio", "SELECT id, descripcion AS texto FROM productos.tipo_precio WHERE id IN (:ids)");
        NOMBRE_SQL.put("moneda", "SELECT id, denominacion AS texto FROM financiero.moneda WHERE id IN (:ids)");
        NOMBRE_SQL.put("usuario", "SELECT u.id, coalesce(pe.nombre, u.nickname) AS texto FROM personas.usuario u "
                + "LEFT JOIN personas.persona pe ON pe.id = u.persona_id WHERE u.id IN (:ids)");
        NOMBRE_SQL.put("subfamilia", "SELECT id, coalesce(descripcion, nombre) AS texto FROM productos.subfamilia WHERE id IN (:ids)");
        NOMBRE_SQL.put("tipoPresentacion", "SELECT id, descripcion AS texto FROM productos.tipo_presentacion WHERE id IN (:ids)");
        NOMBRE_SQL.put("zona", "SELECT id, descripcion AS texto FROM empresarial.zona WHERE id IN (:ids)");
        NOMBRE_SQL.put("lote", "SELECT id, 'Lote ' || numero_lote AS texto FROM operaciones.lote WHERE id IN (:ids)");

        CONTEXTO_SQL.put("PRODUCTO", "SELECT id, descripcion AS texto FROM productos.producto WHERE id IN (:ids)");
        CONTEXTO_SQL.put("PRESENTACION", "SELECT p.id, " + PRESENTACION_TEXTO + " AS texto FROM productos.presentacion p "
                + "JOIN productos.producto pr ON pr.id = p.producto_id WHERE p.id IN (:ids)");
        CONTEXTO_SQL.put("PRECIO_POR_SUCURSAL", "SELECT pps.id, concat_ws(' · ', " + PRESENTACION_TEXTO
                + ", tp.descripcion, s.nombre) AS texto FROM productos.precio_por_sucursal pps "
                + "JOIN productos.presentacion p ON p.id = pps.presentacion_id "
                + "JOIN productos.producto pr ON pr.id = p.producto_id "
                + "LEFT JOIN productos.tipo_precio tp ON tp.id = pps.tipo_precio_id "
                + "LEFT JOIN empresarial.sucursal s ON s.id = pps.sucursal_id WHERE pps.id IN (:ids)");
        CONTEXTO_SQL.put("CODIGO", "SELECT c.id, concat_ws(' · ', " + PRESENTACION_TEXTO + ", c.codigo) AS texto "
                + "FROM productos.codigo c JOIN productos.presentacion p ON p.id = c.presentacion_id "
                + "JOIN productos.producto pr ON pr.id = p.producto_id WHERE c.id IN (:ids)");
        CONTEXTO_SQL.put("COSTO_POR_PRODUCTO", "SELECT c.id, concat_ws(' · ', pr.descripcion, s.nombre) AS texto "
                + "FROM productos.costo_por_producto c JOIN productos.producto pr ON pr.id = c.producto_id "
                + "LEFT JOIN empresarial.sucursal s ON s.id = c.sucursal_id WHERE c.id IN (:ids)");
        String movimiento = "SELECT DISTINCT ON (m.id) m.id, concat_ws(' · ', pr.descripcion, s.nombre) AS texto "
                + "FROM operaciones.movimiento_stock m JOIN productos.producto pr ON pr.id = m.producto_id "
                + "LEFT JOIN empresarial.sucursal s ON s.id = m.sucursal_id WHERE m.id IN (:ids)";
        CONTEXTO_SQL.put("AJUSTE_STOCK", movimiento);
        CONTEXTO_SQL.put("AJUSTE_STOCK_LOTE", movimiento);
        CONTEXTO_SQL.put("ANULACION_STOCK_COMPRA", movimiento);
        CONTEXTO_SQL.put("INVENTARIO", "SELECT i.id, concat_ws(' · ', 'Inventario ' || i.id, s.nombre) AS texto "
                + "FROM operaciones.inventario i LEFT JOIN empresarial.sucursal s ON s.id = i.sucursal_id "
                + "WHERE i.id IN (:ids)");
        CONTEXTO_SQL.put("INVENTARIO_ZONA", "SELECT ip.id, concat_ws(' · ', 'Inventario ' || ip.inventario_id, z.descripcion) "
                + "AS texto FROM operaciones.inventario_producto ip LEFT JOIN empresarial.zona z ON z.id = ip.zona_id "
                + "WHERE ip.id IN (:ids)");
        CONTEXTO_SQL.put("INVENTARIO_ITEM", "SELECT i.id, concat_ws(' · ', " + PRESENTACION_TEXTO
                + ", 'Inventario ' || ip.inventario_id) AS texto FROM operaciones.inventario_producto_item i "
                + "JOIN productos.presentacion p ON p.id = i.presentacion_id "
                + "JOIN productos.producto pr ON pr.id = p.producto_id "
                + "LEFT JOIN operaciones.inventario_producto ip ON ip.id = i.inventario_producto_id WHERE i.id IN (:ids)");
    }
}
