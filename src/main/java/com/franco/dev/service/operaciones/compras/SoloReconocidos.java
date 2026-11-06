package com.franco.dev.service.operaciones.compras;

import java.util.List;

/** Reglas de «cargar solo los reconocidos» de una factura nueva: crear la compra sin los renglones sin producto. */
public final class SoloReconocidos {

    private SoloReconocidos() {
    }

    public static boolean puedeCrear(int reconocidos, int sinProducto, boolean bloqueada, boolean conNota) {
        return !conNota && !bloqueada && sinProducto > 0 && reconocidos > 0;
    }

    /** {reconocido, sinCargar}: Σ cantidad base × precio base sin bonificaciones, igual que totalNota. */
    public static double[] montos(List<CruceFactura.Resultado> res) {
        double reconocido = 0, sinCargar = 0;
        for (CruceFactura.Resultado r : res) {
            if (r.bonificacion) continue;
            double v = r.cantidadBase * r.precioBase;
            if (r.estado == CruceFactura.Estado.SIN_PRODUCTO) sinCargar += v;
            else reconocido += v;
        }
        return new double[]{CruceFactura.redondear(reconocido, 2), CruceFactura.redondear(sinCargar, 2)};
    }
}
