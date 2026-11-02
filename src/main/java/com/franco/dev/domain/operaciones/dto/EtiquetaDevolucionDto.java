package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Etiqueta de separado: una por devolucion, con un solo QR. Escanear el QR
 * trae la devolucion entera con todos sus productos, asi que no hace falta
 * una etiqueta por producto.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EtiquetaDevolucionDto {
    /** DEV-{sucursalId}-{id}. */
    private String identificador;
    private String sucursal;
    /** Nombre del proveedor, o "Sin proveedor". */
    private String proveedor;
    /** dd/MM/yyyy. */
    private String fecha;
    /** Contenido del QR: el identificador de la devolucion. */
    private String qr;
    private List<EtiquetaDevolucionFilaDto> productos;
}
