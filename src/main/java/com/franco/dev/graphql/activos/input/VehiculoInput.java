package com.franco.dev.graphql.activos.input;

import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class VehiculoInput {
    private Long id;
    private Long modeloId;
    private Long tipoVehiculoId;
    private String chapa;
    private String color;
    private Integer anho;
    private Boolean documentacion;
    private Boolean refrigerado;
    private Boolean nuevo;
    private String fechaAdquisicion;
    private BigDecimal primerKilometraje;
    private BigDecimal capacidadKg;
    private Integer capacidadPasajeros;
    private String imagenesVehiculo;
    private String imagenesDocumentos;
    private Long propietarioId;
    private String identificadorInterno;
    private Long tipoCombustibleId;
    private String chasis;
    private Boolean aireAcondicionado;
    private BigDecimal valorEstimado;
    private BigDecimal valorEstimadoPyg;
    private BigDecimal valorEstimadoBrl;
    private Integer mantenimientoMotorIntervalo;
    private Integer mantenimientoCajaIntervalo;
    private String situacionPago;
    private Long proveedorId;
    private Long monedaId;
    private BigDecimal montoTotal;
    private BigDecimal montoYaPagado;
    private Integer cantidadCuotas;
    private Integer cantidadCuotasPagadas;
    private Integer diaVencimiento;
    private Long usuarioId;
    private List<CuotaDetalleInput> cuotasDetalle;
}

