package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.domain.operaciones.enums.TipoDevolucion;
import com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion;
import lombok.Data;

import java.util.List;

@Data
public class DevolucionInput {
    private Long id;
    private TipoDevolucion tipo;
    private Long proveedorId;
    private Long sucursalOrigenId;
    private String fecha;
    private String motivo;
    private DevolucionEstado estado;
    private String identificador;
    private TipoResolucionDevolucion resolucion;
    private String nroNotaCredito;
    private Double montoAcreditado;
    private Long cajaVirtualId;
    private String observacion;
    private Long usuarioId;
    private String creadoEn;

    // Para la creación con ítems relacionados
    private List<DevolucionItemInput> items;
}
