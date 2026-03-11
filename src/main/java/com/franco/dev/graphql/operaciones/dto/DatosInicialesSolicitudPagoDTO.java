package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Datos pre-cargados para crear una solicitud de pago desde una recepción de mercadería.
 * Incluye notas elegibles, moneda/forma de pago sugeridos y fecha de pago propuesta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DatosInicialesSolicitudPagoDTO {
    private List<NotaRecepcion> notas;
    private Long monedaId;
    private Long formaPagoId;
    private String fechaPagoPropuesta;
}
