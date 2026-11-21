package com.franco.dev.service.configuraciones;

import com.franco.dev.domain.configuraciones.enums.TipoOperacion;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos del historial de cambios para el personal administrativo: qué se hizo («Cambió el precio»), cómo se
 * llama cada campo («Último precio de compra» y no «ultimoPrecioCompra») y cada valor con el formato de la
 * pantalla (96.000, Sí/No, 30/09/2028). Sin acceso a la base: lo que hay que buscar lo resuelve
 * {@link HistorialCambiosService}.
 */
public final class HistorialCambiosFormato {

    private HistorialCambiosFormato() {
    }

    /** Grupos del filtro «Tipo de cambio», en el orden en que se muestran. */
    public static final Map<String, Categoria> CATEGORIAS;

    public static final class Categoria {
        public final String codigo;
        public final String nombre;
        public final List<String> tiposEntidad;

        Categoria(String codigo, String nombre, String... tiposEntidad) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.tiposEntidad = Collections.unmodifiableList(Arrays.asList(tiposEntidad));
        }
    }

    static {
        Map<String, Categoria> c = new LinkedHashMap<>();
        agregar(c, new Categoria("PRECIOS", "Precios", "PRECIO_POR_SUCURSAL"));
        agregar(c, new Categoria("COSTOS", "Costos", "COSTO_POR_PRODUCTO"));
        agregar(c, new Categoria("PRODUCTOS", "Productos, presentaciones y códigos", "PRODUCTO", "PRESENTACION", "CODIGO"));
        agregar(c, new Categoria("STOCK", "Ajustes de stock", "AJUSTE_STOCK", "AJUSTE_STOCK_LOTE", "ANULACION_STOCK_COMPRA"));
        agregar(c, new Categoria("INVENTARIOS", "Inventarios", "INVENTARIO", "INVENTARIO_ZONA", "INVENTARIO_ITEM",
                "INVENTARIO_SESION"));
        CATEGORIAS = Collections.unmodifiableMap(c);
    }

    private static void agregar(Map<String, Categoria> mapa, Categoria categoria) {
        mapa.put(categoria.codigo, categoria);
    }

    public static String categoriaDe(String tipoEntidad) {
        for (Categoria c : CATEGORIAS.values()) {
            if (c.tiposEntidad.contains(tipoEntidad)) return c.nombre;
        }
        return "Otros";
    }

    // ---------------------------------------------------------------- qué se hizo

    /**
     * La acción en palabras. {@code camposCambiados}: los campos que cambiaron (en una actualización), para decir
     * «Cambió el precio» en vez de «Modificó un precio» cuando lo que cambió fue justamente el precio.
     */
    public static String accion(String tipoEntidad, TipoOperacion operacion, Map<String, String> camposCambiados) {
        boolean alta = operacion == TipoOperacion.INSERT;
        boolean baja = operacion == TipoOperacion.DELETE || operacion == TipoOperacion.SOFT_DELETE;
        Map<String, String> cambios = camposCambiados != null ? camposCambiados : Collections.emptyMap();
        switch (tipoEntidad == null ? "" : tipoEntidad) {
            case "PRECIO_POR_SUCURSAL":
                if (alta) return "Agregó un precio";
                if (baja) return "Eliminó un precio";
                return cambios.containsKey("precio") ? "Cambió el precio" : "Modificó un precio";
            case "COSTO_POR_PRODUCTO":
                return alta ? "Registró un costo" : baja ? "Eliminó un costo" : "Modificó un costo";
            case "PRODUCTO":
                if (alta) return "Creó el producto";
                if (baja) return "Eliminó el producto";
                if ("false".equals(cambios.get("activo"))) return "Desactivó el producto";
                if ("true".equals(cambios.get("activo"))) return "Activó el producto";
                return "Modificó el producto";
            case "PRESENTACION":
                return alta ? "Agregó una presentación" : baja ? "Eliminó una presentación" : "Modificó una presentación";
            case "CODIGO":
                return alta ? "Agregó un código de barras" : baja ? "Eliminó un código de barras"
                        : "Modificó un código de barras";
            case "AJUSTE_STOCK":
                return alta ? "Ajustó el stock" : baja ? "Eliminó un ajuste de stock" : "Modificó un ajuste de stock";
            case "AJUSTE_STOCK_LOTE":
                return "Ajustó el stock de un lote";
            case "ANULACION_STOCK_COMPRA":
                return "Anuló el stock de una compra";
            case "INVENTARIO":
                if (alta) return "Abrió un inventario";
                if (baja) return "Eliminó un inventario";
                if ("CONCLUIDO".equals(cambios.get("estado"))) return "Concluyó el inventario";
                if ("CANCELADO".equals(cambios.get("estado"))) return "Canceló el inventario";
                return "Modificó un inventario";
            case "INVENTARIO_ZONA":
                return alta ? "Agregó una zona al inventario" : baja ? "Quitó una zona del inventario"
                        : "Modificó una zona del inventario";
            case "INVENTARIO_ITEM":
                return alta ? "Contó un producto en el inventario" : baja ? "Borró un conteo del inventario"
                        : "Corrigió un conteo del inventario";
            case "INVENTARIO_SESION":
                return alta ? "Inició una sesión de inventario" : baja ? "Eliminó una sesión de inventario"
                        : "Modificó una sesión de inventario";
            default:
                String nombre = etiquetaTipo(tipoEntidad);
                return (alta ? "Creó " : baja ? "Eliminó " : "Modificó ") + nombre.toLowerCase(Locale.ROOT);
        }
    }

    private static String etiquetaTipo(String tipoEntidad) {
        if (tipoEntidad == null || tipoEntidad.isEmpty()) return "un registro";
        String t = tipoEntidad.replace('_', ' ').toLowerCase(Locale.ROOT);
        return "un registro de " + t;
    }

    // ---------------------------------------------------------------- campos

    /** Campos técnicos: no le dicen nada a un administrativo (el usuario y la fecha ya están en la fila). */
    public static final Set<String> CAMPOS_OCULTOS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "id", "creadoEn", "modificadoEn", "actualizadoEn", "usuario", "imagenes", "propagado", "version",
            "ACCION", "ACTUALIZACION", "sucursalId", "referencia", "movimientoStock", "inventarioProducto",
            "inventario")));

    public static boolean campoVisible(String campo) {
        return campo != null && !CAMPOS_OCULTOS.contains(campo);
    }

    /**
     * Un campo que se muestra en el detalle de este tipo de cambio. Al crear o borrar, el producto y la presentación
     * ya están en «Sobre qué»; en un movimiento de stock el estado y el tipo son siempre los mismos.
     */
    public static boolean campoVisible(String campo, String tipoEntidad, TipoOperacion operacion) {
        if (!campoVisible(campo)) return false;
        if (operacion != TipoOperacion.UPDATE && ("producto".equals(campo) || "presentacion".equals(campo))) return false;
        boolean movimiento = "AJUSTE_STOCK".equals(tipoEntidad) || "AJUSTE_STOCK_LOTE".equals(tipoEntidad)
                || "ANULACION_STOCK_COMPRA".equals(tipoEntidad);
        return !(movimiento && operacion == TipoOperacion.INSERT && ("estado".equals(campo) || "tipoMovimiento".equals(campo)));
    }

    /**
     * Valores que no se muestran nunca. El registro marca también precios y costos como «sensibles», pero esta
     * pantalla es justamente para ver quién cambió un precio: solo se ocultan claves.
     */
    public static boolean campoSecreto(String campo) {
        if (campo == null) return false;
        String c = campo.toLowerCase(Locale.ROOT);
        return c.contains("password") || c.contains("token") || c.contains("secret");
    }

    private static final Map<String, String> ETIQUETAS = new LinkedHashMap<>();

    static {
        ETIQUETAS.put("precio", "Precio");
        ETIQUETAS.put("descripcion", "Descripción");
        ETIQUETAS.put("descripcionFactura", "Descripción en la factura");
        ETIQUETAS.put("cantidad", "Cantidad");
        ETIQUETAS.put("codigo", "Código de barras");
        ETIQUETAS.put("costoMedio", "Costo medio");
        ETIQUETAS.put("ultimoPrecioCompra", "Último precio de compra");
        ETIQUETAS.put("ultimoPrecioVenta", "Último precio de venta");
        ETIQUETAS.put("cotizacion", "Cotización");
        ETIQUETAS.put("existencia", "Existencia");
        ETIQUETAS.put("moneda", "Moneda");
        ETIQUETAS.put("tipoPrecio", "Tipo de precio");
        ETIQUETAS.put("sucursal", "Sucursal");
        ETIQUETAS.put("presentacion", "Presentación");
        ETIQUETAS.put("producto", "Producto");
        ETIQUETAS.put("tipoPresentacion", "Tipo de presentación");
        ETIQUETAS.put("iva", "IVA");
        ETIQUETAS.put("subfamilia", "Subfamilia");
        ETIQUETAS.put("estado", "Estado");
        ETIQUETAS.put("abierto", "Abierto");
        ETIQUETAS.put("fechaInicio", "Inicio");
        ETIQUETAS.put("fechaFin", "Fin");
        ETIQUETAS.put("tipo", "Tipo");
        ETIQUETAS.put("observacion", "Observación");
        ETIQUETAS.put("cantidadFisica", "Cantidad contada");
        ETIQUETAS.put("cantidadAnterior", "Cantidad en el sistema");
        ETIQUETAS.put("vencimiento", "Vencimiento");
        ETIQUETAS.put("verificado", "Verificado");
        ETIQUETAS.put("revisado", "Revisado");
        ETIQUETAS.put("concluido", "Concluido");
        ETIQUETAS.put("zona", "Zona");
        ETIQUETAS.put("lote", "Lote");
        ETIQUETAS.put("balanza", "Se vende por peso");
        ETIQUETAS.put("garantia", "Garantía");
        ETIQUETAS.put("tiempoGarantia", "Tiempo de garantía");
        ETIQUETAS.put("stock", "Controla stock");
        ETIQUETAS.put("cambiable", "Se puede cambiar");
        ETIQUETAS.put("combo", "Combo");
        ETIQUETAS.put("promocion", "Promoción");
        ETIQUETAS.put("envase", "Envase");
        ETIQUETAS.put("isEnvase", "Es envase");
        ETIQUETAS.put("ingrediente", "Ingrediente");
        ETIQUETAS.put("diasVencimiento", "Días de vencimiento");
        ETIQUETAS.put("tipoConservacion", "Conservación");
        ETIQUETAS.put("unidadPorCaja", "Unidades por caja");
        ETIQUETAS.put("unidadPorCajaSecundaria", "Unidades por caja secundaria");
        ETIQUETAS.put("tipoMovimiento", "Tipo de movimiento");
        ETIQUETAS.put("fechaVerificado", "Verificado el");
        // Al final: en un alta casi siempre son «Sí» y no distinguen nada.
        ETIQUETAS.put("principal", "Principal");
        ETIQUETAS.put("activo", "Activo");
    }

    public static String etiquetaCampo(String campo) {
        String etiqueta = ETIQUETAS.get(campo);
        if (etiqueta != null) return etiqueta;
        if (campo == null || campo.isEmpty()) return "";
        // camelCase → «Camel case»
        String palabras = campo.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(palabras.charAt(0)) + palabras.substring(1);
    }

    /** Orden de los campos en el detalle: primero lo que más se busca (el precio, la cantidad). */
    public static int ordenCampo(String campo) {
        int i = 0;
        for (String c : ETIQUETAS.keySet()) {
            if (c.equals(campo)) return i;
            i++;
        }
        return 1000;
    }

    // ---------------------------------------------------------------- valores

    private static final Pattern REFERENCIA = Pattern.compile("^[A-Za-z]+ \\((ID: )?(-?\\d+)\\)$|^[A-Za-z]+ \\(sin ID\\)$");
    private static final Pattern NUMERO = Pattern.compile("^-?\\d+(\\.\\d+)?([eE]-?\\d+)?$");
    private static final Pattern FECHA_HORA = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2})(:\\d{2}(\\.\\d+)?)?$");
    private static final Pattern FECHA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** «Producto (ID: 12)» o «Producto (sin ID)»: una referencia a otra tabla, que se reemplaza por su nombre. */
    public static boolean esReferencia(String valor) {
        return valor != null && REFERENCIA.matcher(valor.trim()).matches();
    }

    /**
     * Un valor guardado, como lo lee una persona. Las referencias a otras tablas se resuelven antes (con el id del
     * detalle); acá llegan solo si no se encontró el nombre.
     */
    /** Campos de texto aunque traigan solo dígitos: un código de barras no es una cantidad. */
    private static final Set<String> CAMPOS_TEXTO = new HashSet<>(Arrays.asList(
            "codigo", "descripcion", "descripcionFactura", "observacion", "numeroLote", "documento", "telefono"));

    public static String valor(String campo, String crudo) {
        if (crudo != null && CAMPOS_TEXTO.contains(campo)) return crudo.trim();
        return valor(crudo);
    }

    public static String valor(String crudo) {
        if (crudo == null) return null;
        String v = crudo.trim();
        if (v.isEmpty()) return v;
        if ("true".equalsIgnoreCase(v)) return "Sí";
        if ("false".equalsIgnoreCase(v)) return "No";
        if (NUMERO.matcher(v).matches()) return numero(v);
        Matcher fh = FECHA_HORA.matcher(v);
        if (fh.matches()) {
            try {
                LocalDateTime f = LocalDateTime.parse(fh.group(1) + "T" + fh.group(2));
                return f.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? f.format(DD_MM_YYYY) : f.format(DD_MM_YYYY_HH_MM);
            } catch (Exception e) {
                return v;
            }
        }
        if (FECHA.matcher(v).matches()) {
            try {
                return LocalDate.parse(v).format(DD_MM_YYYY);
            } catch (Exception e) {
                return v;
            }
        }
        Matcher ref = REFERENCIA.matcher(v);
        if (ref.matches()) {
            return ref.group(2) != null ? "N° " + ref.group(2) : "—";
        }
        if (v.matches("^[A-Z][A-Z_]+$")) {
            // Un estado o un tipo (ABIERTO, CONCLUIDO, AJUSTE): «Abierto», «Concluido».
            String t = v.replace('_', ' ').toLowerCase(Locale.ROOT);
            return Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        return v;
    }

    /** 96000.0 → «96.000»; 9999.8333 → «9.999,83». */
    public static String numero(String crudo) {
        try {
            double d = Double.parseDouble(crudo);
            DecimalFormatSymbols simbolos = new DecimalFormatSymbols(new Locale("es", "PY"));
            simbolos.setGroupingSeparator('.');
            simbolos.setDecimalSeparator(',');
            DecimalFormat f = new DecimalFormat("#,##0.##", simbolos);
            return f.format(d);
        } catch (NumberFormatException e) {
            return crudo;
        }
    }

    // ---------------------------------------------------------------- producto de cada cambio

    /**
     * El producto al que se refiere una entidad que se registra, para poder buscar después «quién cambió tal
     * producto»: el propio producto, o el de su presentación, o el que tenga en un campo {@code producto}.
     */
    public static Long productoIdDe(Object entidad, String tipoEntidad, Long entidadId) {
        if (entidad == null) return null;
        if ("PRODUCTO".equals(tipoEntidad)) return entidadId;
        try {
            Object producto = campo(entidad, "producto");
            Long id = idDe(producto);
            if (id != null) return id;
            Object presentacion = campo(entidad, "presentacion");
            if (presentacion != null) {
                id = idDe(campo(presentacion, "producto"));
                if (id != null) return id;
            }
            Object presentacionPreTransferencia = campo(entidad, "presentacionPreTransferencia");
            if (presentacionPreTransferencia != null) {
                return idDe(campo(presentacionPreTransferencia, "producto"));
            }
        } catch (Exception e) {
            // Sin producto: el cambio queda igual, solo no aparece al filtrar por producto.
        }
        return null;
    }

    private static Object campo(Object objeto, String nombre) throws IllegalAccessException {
        if (objeto == null) return null;
        // Primero el getter: en un proxy LAZY de Hibernate los campos están vacíos hasta inicializarlo.
        try {
            java.lang.reflect.Method getter = objeto.getClass()
                    .getMethod("get" + Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1));
            return getter.invoke(objeto);
        } catch (NoSuchMethodException e) {
            // sin getter: se lee el campo
        } catch (java.lang.reflect.InvocationTargetException e) {
            return null;
        }
        Class<?> clase = objeto.getClass();
        while (clase != null && clase != Object.class) {
            try {
                Field f = clase.getDeclaredField(nombre);
                f.setAccessible(true);
                return f.get(objeto);
            } catch (NoSuchFieldException e) {
                clase = clase.getSuperclass();
            }
        }
        return null;
    }

    private static Long idDe(Object objeto) throws IllegalAccessException {
        if (objeto == null) return null;
        if (objeto instanceof com.franco.dev.config.Identifiable) {
            Object id = ((com.franco.dev.config.Identifiable<?>) objeto).getId();
            return id instanceof Number ? ((Number) id).longValue() : null;
        }
        Object id = campo(objeto, "id");
        return id instanceof Number ? ((Number) id).longValue() : null;
    }
}
