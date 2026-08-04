package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que se vendió de un lote sin cliente identificado.
 *
 * Es el complemento honesto de {@link ClienteLoteDto}: sin este número la lista de clientes se lee
 * como si fuera todo lo que salió del lote, cuando en realidad la venta de mostrador es la
 * abrumadora mayoría y no deja rastro de a quién fue.
 *
 * Va como consulta aparte porque el listado de clientes es una página de Spring y una página no
 * puede llevar un total ajeno a su contenido.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MostradorLoteDto {

    /** Ventas de mostrador que tocaron el lote. */
    private Long ventas;
    /** Unidades que salieron en esas ventas, en positivo. */
    private Double cantidad;
}
