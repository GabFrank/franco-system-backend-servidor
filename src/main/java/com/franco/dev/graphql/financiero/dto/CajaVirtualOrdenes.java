package com.franco.dev.graphql.financiero.dto;

import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solo los órdenes configurables de una caja (JSON arrays; null = orden por defecto). Es lo único de la
 * configuración que necesita quien opera la caja con el rol acotado: los diálogos lo usan para ordenar,
 * nunca para decidir permisos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CajaVirtualOrdenes {
    private String monedasOrden;
    private String sucursalesOrden;
    private String operacionesFinancierasOrden;
    private String operacionesFinancierasOcultas;

    public static CajaVirtualOrdenes de(CajaVirtualConfiguracion c) {
        if (c == null) return new CajaVirtualOrdenes();
        return new CajaVirtualOrdenes(c.getMonedasOrden(), c.getSucursalesOrden(),
                c.getOperacionesFinancierasOrden(), c.getOperacionesFinancierasOcultas());
    }
}
