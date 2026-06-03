package com.franco.dev.service.grafico.excel;

import com.franco.dev.domain.financiero.GastoPorCategoria;
import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import com.franco.dev.domain.operaciones.VentaPorCiudad;
import com.franco.dev.domain.operaciones.VentaPorFuncionario;
import com.franco.dev.domain.operaciones.VentaPorSucursal;
import com.franco.dev.graphql.financiero.dto.FormaPagoEstadistica;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class GraficoDesgloseFila {
    String etiqueta;
    double total;
    double cantidadVentas;
    List<DesglosePeriodoGrafico> desglosePeriodos;
    List<DesgloseAnhoGrafico> desgloseAnhos;

    public static GraficoDesgloseFila desdeCiudad(VentaPorCiudad item) {
        return new GraficoDesgloseFila(
                nombreCiudad(item),
                valor(item.getTotal()),
                valor(item.getCantidadVentas()),
                copiar(item.getDesglosePeriodos()),
                copiarAnhos(item.getDesgloseAnhos())
        );
    }

    public static GraficoDesgloseFila desdeSucursal(VentaPorSucursal item) {
        return new GraficoDesgloseFila(
                item.getNombre() != null ? item.getNombre() : "Sucursal " + item.getSucId(),
                valor(item.getTotal()),
                0,
                copiar(item.getDesglosePeriodos()),
                copiarAnhos(item.getDesgloseAnhos())
        );
    }

    public static GraficoDesgloseFila desdeFuncionario(VentaPorFuncionario item) {
        return new GraficoDesgloseFila(
                item.getFuncionario() != null ? item.getFuncionario() : "Funcionario " + item.getId(),
                valor(item.getTotal()),
                item.getCantidad() != null ? item.getCantidad().doubleValue() : 0,
                copiar(item.getDesglosePeriodos()),
                copiarAnhos(item.getDesgloseAnhos())
        );
    }

    public static GraficoDesgloseFila desdeGastoCategoria(GastoPorCategoria item) {
        return new GraficoDesgloseFila(
                item.getCategoria() != null ? item.getCategoria() : "Sin categoría",
                valor(item.getTotal()),
                item.getCantidad() != null ? item.getCantidad().doubleValue() : 0,
                copiar(item.getDesglosePeriodos()),
                copiarAnhos(item.getDesgloseAnhos())
        );
    }

    public static GraficoDesgloseFila desdeFormaPago(FormaPagoEstadistica item) {
        double total = item.getTotalMonto() != null ? item.getTotalMonto().doubleValue() : 0;
        double cant = item.getCantidadTransacciones() != null ? item.getCantidadTransacciones().doubleValue() : 0;
        return new GraficoDesgloseFila(
                item.getDescripcion() != null ? item.getDescripcion() : "Forma de pago",
                total,
                cant,
                copiar(item.getDesglosePeriodos()),
                copiarAnhos(item.getDesgloseAnhos())
        );
    }

    public static GraficoDesgloseFila desdeProducto(ProductoVendidoEstadistica item) {
        return new GraficoDesgloseFila(
                item.getDescripcion() != null ? item.getDescripcion() : "Producto " + item.getProductoId(),
                valor(item.getTotalMonto()),
                valor(item.getCantidad()),
                copiar(item.getDesglosePeriodos()),
                copiarAnhos(item.getDesgloseAnhos())
        );
    }

    private static String nombreCiudad(VentaPorCiudad item) {
        if (item.getNombre() != null && !item.getNombre().isBlank()) {
            return item.getNombre();
        }
        return "Ciudad " + (item.getCiudadId() != null ? item.getCiudadId() : "");
    }

    private static double valor(Double v) {
        return v != null ? v : 0;
    }

    private static List<DesglosePeriodoGrafico> copiar(List<DesglosePeriodoGrafico> lista) {
        return lista != null ? new ArrayList<>(lista) : new ArrayList<>();
    }

    private static List<DesgloseAnhoGrafico> copiarAnhos(List<DesgloseAnhoGrafico> lista) {
        return lista != null ? new ArrayList<>(lista) : new ArrayList<>();
    }
}
