package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import lombok.Data;

import java.util.List;

@Data
public class ProductoAgrupadoDTO {
    private Producto producto;
    private Double cantidadTotalEsperada;
    private Presentacion presentacionConsolidada;
    private List<NotaRecepcionItemDistribucion> distribuciones;
}

