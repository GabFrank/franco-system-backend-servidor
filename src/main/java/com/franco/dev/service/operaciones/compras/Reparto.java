package com.franco.dev.service.operaciones.compras;

/** Reparto de una cantidad en proporción a unos pesos (la distribución por sucursal del pedido). */
final class Reparto {

    private Reparto() {
    }

    /**
     * Las partes suman exactamente {@code total}. Si el total es entero, las partes también (método del mayor
     * resto): 80 unidades en tres sucursales con pesos 1:1:1 dan 27, 27 y 26, no 26,667 cada una. Sin pesos (todos
     * cero), se reparte en partes iguales.
     */
    static double[] proporcional(double total, double[] pesos) {
        int n = pesos.length;
        double[] partes = new double[n];
        if (n == 0) return partes;
        double suma = 0;
        for (double p : pesos) suma += Math.max(0, p);
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = suma > 0 ? Math.max(0, pesos[i]) / suma : 1.0 / n;
        boolean entero = Math.abs(total - Math.rint(total)) < 1e-9;
        if (entero) {
            long t = Math.round(total);
            long asignado = 0;
            double[] restos = new double[n];
            for (int i = 0; i < n; i++) {
                double exacto = t * w[i];
                partes[i] = Math.floor(exacto);
                restos[i] = exacto - partes[i];
                asignado += (long) partes[i];
            }
            for (long k = asignado; k < t; k++) {
                int mayor = 0;
                for (int i = 1; i < n; i++) if (restos[i] > restos[mayor]) mayor = i;
                partes[mayor] += 1;
                restos[mayor] = -1;
            }
            return partes;
        }
        double acumulado = 0;
        for (int i = 0; i < n - 1; i++) {
            partes[i] = Math.round(total * w[i] * 10000.0) / 10000.0;
            acumulado += partes[i];
        }
        partes[n - 1] = Math.round((total - acumulado) * 10000.0) / 10000.0;
        return partes;
    }
}
