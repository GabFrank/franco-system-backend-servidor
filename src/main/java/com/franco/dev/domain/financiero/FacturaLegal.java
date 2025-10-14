package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "factura_legal", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class FacturaLegal implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "timbrado_detalle_id", nullable = true)
    private TimbradoDetalle timbradoDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "caja_id", referencedColumnName = "id", insertable = false, updatable = false),
            @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
    })
    private PdvCaja caja;

    private Boolean viaTributaria;

    private Boolean autoimpreso;

    @Column(name = "numero_factura")
    private Integer numeroFactura;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "venta_id", referencedColumnName = "id", insertable = false, updatable = false),
            @JoinColumn(name = "sucursal_id", referencedColumnName = "sucursal_id", insertable = false, updatable = false)
    })
    private Venta venta;

    private LocalDateTime fecha;
    private Boolean credito;
    private String nombre;
    private String ruc;
    private String direccion;
    private String cdc;

    @Column(name = "iva_parcial_0")
    private Double ivaParcial0;
    @Column(name = "iva_parcial_5")
    private Double ivaParcial5;
    @Column(name = "iva_parcial_10")
    private Double ivaParcial10;
    @Column(name = "total_parcial_0")
    private Double totalParcial0;
    @Column(name = "total_parcial_5")
    private Double totalParcial5;
    @Column(name = "total_parcial_10")
    private Double totalParcial10;

    private Double totalFinal;
    private Double descuento;

    private Boolean activo;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @OneToOne(mappedBy = "facturaLegal", fetch = FetchType.LAZY)
    private DocumentoElectronico documentoElectronico;
}



