package com.franco.dev.service.operaciones.compras;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Cruza los renglones de la factura del proveedor con los ítems del pedido. Lógica pura: no toca la base.
 *
 * Reproduce las reglas del diálogo «Editar ítem de nota» del desktop ({@code edit-nota-recepcion-item-dialog}),
 * que hasta ahora vivían solo en la pantalla:
 * <ul>
 *   <li>diferencia de cantidad, precio (misma moneda, no bonificación), presentación o bonificación contra el
 *   pedido ⇒ observación obligatoria;</li>
 *   <li>un producto que no estaba en el pedido se puede agregar, con observación (el desktop lo permite).</li>
 * </ul>
 *
 * Todo se compara en <b>unidades base</b>: cantidad × factor, precio ÷ factor. El precio de la nota es el de la
 * factura <b>con IVA incluido</b>, que es como lo cargan hoy los compradores en el desktop (verificado contra las
 * notas de MARVI: 5.218, 9.565 y 6.088 son los precios del KuDE).
 */
public final class CruceFactura {

    /** Tolerancia de cantidades, la misma del desktop. */
    static final double TOLERANCIA_CANTIDAD = 0.001;
    /** Un guaraní de diferencia es redondeo de la división por el factor, no un cambio de precio. */
    static final double TOLERANCIA_PRECIO = 0.5;

    public enum Estado { OK, DIFERENCIA, FUERA_DE_PEDIDO, SIN_PRODUCTO }

    private CruceFactura() {
    }

    /** Un renglón de la factura con el producto ya resuelto (o sin resolver). */
    public static class Renglon {
        public long id;
        public Long productoId;
        public Long presentacionId;
        public String presentacionDescripcion;
        /** Unidades base por unidad facturada. */
        public Double factor;
        public double cantidad;
        public double precioUnitario;
        public double descuentoUnitario;
        public Double porcentajeDescuento;
        public Integer tasaIva;
        /** El IVA que tiene el producto en el sistema. */
        public Integer ivaProducto;
        public String observacion;
    }

    /** Un ítem del pedido, en unidades base. */
    public static class ItemPedido {
        public long id;
        public long productoId;
        public Long presentacionId;
        public String presentacionDescripcion;
        public double cantidadPendiente;
        public Double precioUnitario;
        public boolean bonificacion;
    }

    public static class Resultado {
        public long renglonId;
        public Estado estado;
        public Long pedidoItemId;
        public boolean bonificacion;
        /** Cantidad que va a la nota, en unidades base. */
        public double cantidadBase;
        /** Precio por unidad base que va a la nota (0 en una bonificación). */
        public double precioBase;
        public Double pedidoCantidadPendiente;
        public Double pedidoPrecio;
        public final List<String> diferencias = new ArrayList<>();
        /** Aviso de IVA (precio del pedido sin IVA, o IVA distinto al del producto). No exige observación. */
        public final List<String> avisosIva = new ArrayList<>();
        /** Otros avisos que no exigen observación (ej. bonificación que no estaba en el pedido). */
        public final List<String> avisos = new ArrayList<>();
        public boolean requiereObservacion;
        public boolean observacionFaltante;
    }

    public static boolean esBonificacion(Renglon r) {
        if (r.porcentajeDescuento != null && r.porcentajeDescuento >= 99.99) return true;
        return r.precioUnitario - r.descuentoUnitario <= 0.0001;
    }

    /**
     * @param mismaMoneda la factura está en la moneda del pedido. Si no, el precio no se compara (como el desktop:
     *                    comparar 15.000 Gs contra 2 US$ no dice nada).
     */
    public static List<Resultado> cruzar(List<Renglon> renglones, List<ItemPedido> items, boolean mismaMoneda) {
        Map<Long, Double> pendiente = new HashMap<>();
        for (ItemPedido i : items) {
            pendiente.put(i.id, Math.max(0, i.cantidadPendiente));
        }
        Map<Long, Resultado> porRenglon = new HashMap<>();
        // Primero lo cobrado y después lo bonificado: la bonificación no tiene que «comerse» el pendiente del
        // ítem cobrado del mismo producto.
        for (boolean bonif : new boolean[]{false, true}) {
            for (Renglon r : renglones) {
                if (esBonificacion(r) != bonif) continue;
                porRenglon.put(r.id, cruzarRenglon(r, items, pendiente, mismaMoneda));
            }
        }
        List<Resultado> resultados = new ArrayList<>();
        for (Renglon r : renglones) {
            resultados.add(porRenglon.get(r.id));
        }
        return resultados;
    }

    private static Resultado cruzarRenglon(Renglon r, List<ItemPedido> items, Map<Long, Double> pendiente,
                                           boolean mismaMoneda) {
        Resultado res = new Resultado();
        res.renglonId = r.id;
        res.bonificacion = esBonificacion(r);
        double factor = r.factor != null && r.factor > 0 ? r.factor : 1.0;
        res.cantidadBase = redondear(r.cantidad * factor, 4);
        res.precioBase = res.bonificacion ? 0.0 : redondear((r.precioUnitario - r.descuentoUnitario) / factor, 4);

        if (r.productoId == null) {
            res.estado = Estado.SIN_PRODUCTO;
            return res;
        }
        avisoIvaDelProducto(r, res);

        ItemPedido item = elegirItem(r, res.bonificacion, items, pendiente);
        if (item == null) {
            if (res.bonificacion && items.stream().anyMatch(i -> i.productoId == r.productoId)) {
                // Bonificación de un producto del pedido que el pedido no esperaba: buena noticia, no discrepancia
                res.estado = Estado.OK;
                res.avisos.add("Bonificación: " + cantidad(res.cantidadBase) + " u. sin cargo que el pedido no tenía");
                return res;
            }
            res.estado = Estado.FUERA_DE_PEDIDO;
            res.requiereObservacion = true;
            res.observacionFaltante = vacio(r.observacion);
            return res;
        }

        res.pedidoItemId = item.id;
        double pendienteAntes = pendiente.getOrDefault(item.id, 0.0);
        res.pedidoCantidadPendiente = pendienteAntes;
        res.pedidoPrecio = item.precioUnitario;
        pendiente.put(item.id, pendienteAntes - res.cantidadBase);

        if (Math.abs(res.cantidadBase - pendienteAntes) > TOLERANCIA_CANTIDAD) {
            res.diferencias.add(pendienteAntes <= TOLERANCIA_CANTIDAD
                    ? "Cantidad: el pedido ya no tenía pendiente → factura " + cantidad(res.cantidadBase) + " u."
                    : "Cantidad: faltaban " + cantidad(pendienteAntes) + " u. → factura " + cantidad(res.cantidadBase) + " u.");
        }
        if (!res.bonificacion && mismaMoneda && item.precioUnitario != null
                && Math.abs(res.precioBase - item.precioUnitario) > TOLERANCIA_PRECIO) {
            res.diferencias.add("Precio: pedido " + importe(item.precioUnitario) + " → factura " + importe(res.precioBase));
            avisoIvaDelPedido(r, res, item.precioUnitario);
        }
        if (item.presentacionId != null && r.presentacionId != null && !item.presentacionId.equals(r.presentacionId)) {
            res.diferencias.add("Presentación: pedido " + texto(item.presentacionDescripcion) + " → factura "
                    + texto(r.presentacionDescripcion));
        }
        if (item.bonificacion != res.bonificacion) {
            res.diferencias.add("Bonificación: pedido " + (item.bonificacion ? "sí" : "no") + " → factura "
                    + (res.bonificacion ? "sí" : "no"));
        }
        res.estado = res.diferencias.isEmpty() ? Estado.OK : Estado.DIFERENCIA;
        res.requiereObservacion = !res.diferencias.isEmpty();
        res.observacionFaltante = res.requiereObservacion && vacio(r.observacion);
        return res;
    }

    /**
     * Una factura que <b>crea</b> la compra: todavía no hay pedido contra el cual comparar, el pedido sale de la
     * factura. Cada renglón con producto queda OK; sin producto, SIN_PRODUCTO. Lo único que se avisa es el IVA del
     * producto distinto al de la factura.
     */
    public static List<Resultado> sinPedido(List<Renglon> renglones) {
        List<Resultado> resultados = new ArrayList<>();
        for (Renglon r : renglones) {
            Resultado res = new Resultado();
            res.renglonId = r.id;
            res.bonificacion = esBonificacion(r);
            double factor = r.factor != null && r.factor > 0 ? r.factor : 1.0;
            res.cantidadBase = redondear(r.cantidad * factor, 4);
            res.precioBase = res.bonificacion ? 0.0 : redondear((r.precioUnitario - r.descuentoUnitario) / factor, 4);
            if (r.productoId == null) {
                res.estado = Estado.SIN_PRODUCTO;
            } else {
                res.estado = Estado.OK;
                avisoIvaDelProducto(r, res);
                if (res.bonificacion) {
                    res.avisos.add("Bonificación: " + cantidad(res.cantidadBase) + " u. sin cargo");
                }
            }
            resultados.add(res);
        }
        return resultados;
    }

    /**
     * Compara el precio de la factura con el de la última compra del producto (por unidad base, misma moneda).
     * Si la diferencia es justo el IVA, lo dice: la compra anterior se cargó sin IVA, o esta factura trae el IVA
     * sumado dos veces. Si no, avisa solo los cambios grandes (más de un 15 %).
     *
     * @return el aviso, o null si no hay nada que decir
     */
    public static String avisoUltimaCompra(double precioBase, Double ultimoPrecio, Integer tasaIva) {
        if (ultimoPrecio == null || ultimoPrecio <= 0 || precioBase <= 0) return null;
        int[] tasas = tasaIva != null && tasaIva > 0 ? new int[]{tasaIva} : new int[]{10, 5};
        for (int tasa : tasas) {
            double tolerancia = Math.max(1.0, ultimoPrecio * 0.002);
            if (Math.abs(precioBase - ultimoPrecio * (1 + tasa / 100.0)) <= tolerancia) {
                return "IVA: la última compra se cargó a " + importe(ultimoPrecio) + ", que es este precio sin el IVA del "
                        + tasa + " %. La nota se carga con IVA, como la factura.";
            }
            if (Math.abs(precioBase * (1 + tasa / 100.0) - ultimoPrecio) <= tolerancia) {
                return "IVA: la última compra se cargó a " + importe(ultimoPrecio) + ", que es este precio más el "
                        + tasa + " %: revisá si la factura trae el precio sin IVA.";
            }
        }
        double variacion = (precioBase - ultimoPrecio) / ultimoPrecio;
        if (Math.abs(variacion) > 0.15) {
            return "Precio " + (variacion > 0 ? "más alto" : "más bajo") + " que la última compra: "
                    + importe(ultimoPrecio) + " → " + importe(precioBase) + " ("
                    + (variacion > 0 ? "+" : "") + Math.round(variacion * 100) + " %).";
        }
        return null;
    }

    /** Hasta dónde se acepta que el precio por unidad difiera de la última compra para elegir el bulto. */
    static final double TOLERANCIA_BULTO = 0.25;
    /** Una proporción «casi entera» entre precio y última compra (141.865 / 4.729 = 30,0). */
    static final double TOLERANCIA_ENTERO = 0.03;
    /**
     * Desempate: la unidad multiplicada por la proporción del precio siempre cierra, así que pierde contra un bulto
     * que además dice la descripción o arma una presentación (804 = 12 bolsas de 67 le gana a 805 sueltas).
     */
    private static final double PENALIDAD_SOLO_PRECIO = 0.02;

    /**
     * Cuántas unidades base trae lo que factura el proveedor, mirando el precio contra la última compra.
     *
     * El proveedor factura por caja, display o bulto aunque el código sea el de la unidad: en GENFI «BONOBON
     * 30X15G (12)» es un bulto de 360 (30 × 12) y «SALADIX JAMON 6*100GR» uno de 30 (lo dice el precio, no la
     * descripción). Candidatos:
     * <ul>
     *   <li>los números de bulto de la descripción y sus productos;</li>
     *   <li>cada presentación del producto, y la presentación por un entero cuando el precio lo sugiere
     *   (804 = 12 × 67);</li>
     * </ul>
     * Primero se prueba lo explícito (unidad, presentaciones, descripción); lo deducido del precio solo si eso no
     * cierra. En cada nivel gana el que deja el precio por unidad más cerca de la última compra, dentro de
     * {@link #TOLERANCIA_BULTO}.
     *
     * @return el factor, o null si no hay compra anterior o ninguno cierra (no se adivina)
     */
    public static Double factorPorPrecio(double precioFactura, Double ultimoPrecioUnidad,
                                         java.util.Collection<Double> presentaciones, java.util.List<Integer> numerosDescripcion) {
        if (ultimoPrecioUnidad == null || ultimoPrecioUnidad <= 0 || precioFactura <= 0) return null;
        // 1.º lo explícito: la unidad, las presentaciones y lo que dice la descripción
        java.util.Set<Double> explicitos = new java.util.LinkedHashSet<>();
        explicitos.add(1.0);
        java.util.List<Double> bases = new java.util.ArrayList<>();
        bases.add(1.0);
        if (presentaciones != null) {
            for (Double c : presentaciones) {
                if (c != null && c > 0) {
                    explicitos.add(c);
                    bases.add(c);
                }
            }
        }
        java.util.Set<Integer> deDescripcion = productosDe(numerosDescripcion);
        for (Integer n : deDescripcion) explicitos.add((double) n);
        java.util.Map<Double, Boolean> nivel1 = new java.util.LinkedHashMap<>();
        for (Double c : explicitos) nivel1.put(c, false);
        Double elegido = mejorFactor(precioFactura, ultimoPrecioUnidad, nivel1);
        if (elegido != null) return elegido;

        // 2.º solo si nada de eso cierra: lo deducido del precio (una presentación o la unidad por un entero)
        double proporcion = precioFactura / ultimoPrecioUnidad;
        java.util.Map<Double, Boolean> nivel2 = new java.util.LinkedHashMap<>();
        for (Double base : bases) {
            double k = proporcion / base;
            double redondo = Math.rint(k);
            if (redondo < 2 || Math.abs(k - redondo) / redondo > TOLERANCIA_ENTERO) continue;
            double bulto = base * redondo;
            // Si la descripción dice números, el bulto tiene que ser múltiplo de alguno
            // (SALADIX «6*100GR» → 30 = 5 × 6 sí; GOMAS «12X30G (12)» → 44 no)
            boolean coherente = deDescripcion.isEmpty()
                    || deDescripcion.stream().anyMatch(n -> Math.abs(bulto / n - Math.rint(bulto / n)) < 1e-9);
            if (coherente) nivel2.putIfAbsent(bulto, base <= 1.0);
        }
        return mejorFactor(precioFactura, ultimoPrecioUnidad, nivel2);
    }

    private static Double mejorFactor(double precioFactura, double ultimo, java.util.Map<Double, Boolean> candidatos) {
        Double mejor = null;
        double mejorPuntaje = Double.MAX_VALUE;
        for (java.util.Map.Entry<Double, Boolean> c : candidatos.entrySet()) {
            double error = Math.abs(precioFactura / c.getKey() - ultimo) / ultimo;
            if (error > TOLERANCIA_BULTO) continue;
            double puntaje = error + (c.getValue() ? PENALIDAD_SOLO_PRECIO : 0);
            if (puntaje < mejorPuntaje - 1e-9) {
                mejorPuntaje = puntaje;
                mejor = c.getKey();
            }
        }
        return mejor;
    }

    /** Los números y todos sus productos: [30, 12] → 30, 12, 360. */
    static java.util.Set<Integer> productosDe(java.util.List<Integer> numeros) {
        java.util.Set<Integer> productos = new java.util.LinkedHashSet<>();
        if (numeros == null) return productos;
        java.util.List<Integer> lista = new java.util.ArrayList<>();
        for (Integer n : numeros) if (n != null && n > 1) lista.add(n);
        int total = lista.size();
        for (int mascara = 1; mascara < (1 << Math.min(total, 6)); mascara++) {
            long p = 1;
            for (int i = 0; i < Math.min(total, 6); i++) if ((mascara & (1 << i)) != 0) p *= lista.get(i);
            if (p > 1 && p < 100000) productos.add((int) p);
        }
        return productos;
    }

    /**
     * El ítem del pedido del mismo producto. Una bonificación solo va contra un ítem bonificado del pedido; lo
     * cobrado prefiere uno no bonificado, con pendiente y de la misma presentación.
     */
    private static ItemPedido elegirItem(Renglon r, boolean bonif, List<ItemPedido> items, Map<Long, Double> pendiente) {
        ItemPedido mejor = null;
        int mejorPuntaje = -1;
        for (ItemPedido i : items) {
            if (i.productoId != r.productoId) continue;
            if (bonif && !i.bonificacion) continue;
            boolean conPendiente = pendiente.getOrDefault(i.id, 0.0) > TOLERANCIA_CANTIDAD;
            if (bonif && !conPendiente) continue;
            int puntaje = (conPendiente ? 4 : 0)
                    + (i.bonificacion == bonif ? 2 : 0)
                    + (Objects.equals(i.presentacionId, r.presentacionId) ? 1 : 0);
            if (puntaje > mejorPuntaje) {
                mejor = i;
                mejorPuntaje = puntaje;
            }
        }
        return mejor;
    }

    /** ¿La diferencia de precio es justo el IVA? Es el caso típico de un pedido cargado sin IVA. */
    private static void avisoIvaDelPedido(Renglon r, Resultado res, double precioPedido) {
        if (precioPedido <= 0) return;
        int[] tasas = r.tasaIva != null && r.tasaIva > 0 ? new int[]{r.tasaIva} : new int[]{10, 5};
        for (int tasa : tasas) {
            double tolerancia = Math.max(1.0, precioPedido * 0.002);
            if (Math.abs(res.precioBase - precioPedido * (1 + tasa / 100.0)) <= tolerancia) {
                res.avisosIva.add("El precio de la factura es el del pedido más el IVA del " + tasa + " %: "
                        + "parece que el pedido se cargó sin IVA. La nota se carga con IVA, como la factura.");
                return;
            }
            if (Math.abs(res.precioBase * (1 + tasa / 100.0) - precioPedido) <= tolerancia) {
                res.avisosIva.add("El precio del pedido es el de la factura más el " + tasa + " %: "
                        + "revisá si el pedido tenía el IVA sumado dos veces.");
                return;
            }
        }
    }

    private static void avisoIvaDelProducto(Renglon r, Resultado res) {
        if (r.tasaIva == null || r.ivaProducto == null) return;
        if (!r.tasaIva.equals(r.ivaProducto)) {
            res.avisosIva.add("En el sistema el producto tiene " + iva(r.ivaProducto) + " y la factura dice "
                    + iva(r.tasaIva) + ".");
        }
    }

    // --------------------------------------------------------------------------------- formato

    static String iva(Integer tasa) {
        return tasa == null ? "IVA sin dato" : tasa == 0 ? "exenta" : "IVA " + tasa + " %";
    }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String texto(String s) {
        return vacio(s) ? "sin presentación" : s;
    }

    static double redondear(double valor, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(valor * f) / f;
    }

    static String cantidad(double valor) {
        DecimalFormat f = new DecimalFormat("#,##0.###", new DecimalFormatSymbols(new Locale("es", "PY")));
        return f.format(valor);
    }

    static String importe(double valor) {
        DecimalFormat f = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(new Locale("es", "PY")));
        return f.format(valor);
    }
}
