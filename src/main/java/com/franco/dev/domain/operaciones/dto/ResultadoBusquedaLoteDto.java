package com.franco.dev.domain.operaciones.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta de buscarLotePorNumero.
 *
 * encontrado=false NO significa "este lote nunca entro": significa que no hay registro. Lo que se
 * puede afirmar mas alla de eso lo dice trazabilidad, que solo viene cuando se busco con producto.
 */
@Data
@NoArgsConstructor
public class ResultadoBusquedaLoteDto {
    private String numeroBuscado;
    /** Clave normalizada con la que se busco (mayusculas, sin separadores ni ceros a la izquierda). */
    private String claveBusqueda;
    private Boolean encontrado;
    /** true: coincidencias exactas por clave. false: parciales (el numero esta contenido). */
    private Boolean coincidenciaExacta;
    private List<LoteEncontradoDto> lotes = new ArrayList<>();
    private TrazabilidadProductoDto trazabilidad;
}
