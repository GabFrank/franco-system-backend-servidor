package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.FuenteVerdadVencimiento;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVencidoViewDTO {

    private Long id;
    private Long presentacionId;
    private Double presentacionCantidad;
    private Long productoId;
    private String productoDescripcion;
    private String codigoBarras;
    private Double cantidad;
    private LocalDateTime vencimiento;
    private Long inventarioProductoId;
    private Long sucursalId;
    private String sucursalNombre;
    private String sectorDescripcion;
    private String zonaDescripcion;
    private Long usuarioId;
    private String usuarioNickname;

    private FuenteVerdadVencimiento fuenteVerdad;
    private Long origenId;
    private LocalDateTime fechaFuente;
    private Long inventarioId;
    private Double cantidadInventario;
    private LocalDateTime vencimientoInventario;
    private String referenciaInventario;
    private String detalleFuente;

    private Integer diasVencimiento;
    private String diasVencimientoTexto;
    private String vencimientoColor;
    private String diasVencimientoClase;
}
