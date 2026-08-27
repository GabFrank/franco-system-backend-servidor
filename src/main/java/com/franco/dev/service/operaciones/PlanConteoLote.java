package com.franco.dev.service.operaciones;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Qué escribe la finalización de una toma para UN producto con control de lote en UNA sucursal.
 *
 * Es el resultado de {@link InventarioLoteService#planificar}, separado del acto de guardar para
 * que la decisión —que es la parte que puede salir mal en silencio— se pueda leer y probar sola.
 *
 * Todas las cantidades van en UNIDADES BASE, igual que el ledger.
 *
 * INVARIANTE: {@code suma(cantidadPorLote) + cantidadSinTrazar == cantidadMovimiento}. Es el mismo
 * que sostiene {@link AjusteStockLoteService}: nunca una fila hija suelta, nunca un padre que no
 * coincida con sus hijas.
 */
public final class PlanConteoLote {

    private final boolean omitido;
    private final List<Long> lotesSinContar;
    private final double cantidadMovimiento;
    private final Map<Long, Double> cantidadPorLote;
    private final double cantidadSinTrazar;

    PlanConteoLote(boolean omitido, List<Long> lotesSinContar, double cantidadMovimiento,
                   Map<Long, Double> cantidadPorLote, double cantidadSinTrazar) {
        this.omitido = omitido;
        this.lotesSinContar = lotesSinContar;
        this.cantidadMovimiento = cantidadMovimiento;
        this.cantidadPorLote = cantidadPorLote;
        this.cantidadSinTrazar = cantidadSinTrazar;
    }

    /**
     * El producto queda ENTERO fuera del ajuste: ni movimiento agregado ni filas del ledger.
     *
     * Pasa cuando algún lote con saldo no fue contado. Es la misma regla que la finalización ya
     * aplica al ítem con {@code cantidad == null}: un lote sin contar no es un lote en cero, y
     * tomarlo como cero le borraría el stock a mercadería que nadie miró.
     */
    public boolean isOmitido() {
        return omitido;
    }

    /** Los lotes con saldo que ningún renglón contó. Vacío si no se omitió nada. */
    public List<Long> getLotesSinContar() {
        return lotesSinContar;
    }

    /** Lo que va en el {@code MovimientoStock} agregado: contado − existencia. */
    public double getCantidadMovimiento() {
        return cantidadMovimiento;
    }

    /** Por lote contado: contado − saldo de ese lote. Puede ser cero. */
    public Map<Long, Double> getCantidadPorLote() {
        return cantidadPorLote;
    }

    /**
     * Lo que las filas por lote no explican, que va a la fila sintética {@code SIN LOTE}.
     *
     * Contar por lote es, en el fondo, una atribución: el stock que estaba en el bucket sin trazar
     * pasa a tener dueño. Esta cantidad es la que lo saca de ahí.
     */
    public double getCantidadSinTrazar() {
        return cantidadSinTrazar;
    }

    /** No hay nada que escribir: lo contado coincide con el sistema, lote por lote. */
    public boolean isVacio() {
        if (Math.abs(cantidadMovimiento) > InventarioLoteService.EPSILON
                || Math.abs(cantidadSinTrazar) > InventarioLoteService.EPSILON) {
            return false;
        }
        for (Double valor : cantidadPorLote.values()) {
            if (Math.abs(valor) > InventarioLoteService.EPSILON) {
                return false;
            }
        }
        return true;
    }

    static PlanConteoLote omitir(List<Long> lotesSinContar) {
        return new PlanConteoLote(true, lotesSinContar, 0.0, Collections.emptyMap(), 0.0);
    }
}
