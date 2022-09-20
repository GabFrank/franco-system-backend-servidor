package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinColumnsOrFormulas;
import org.hibernate.annotations.JoinFormula;

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "caja_id", referencedColumnName = "id"))
    })
    private PdvCaja caja;

    private Boolean viaTributaria;

    private Boolean autoimpreso;

    @Column(name = "numero_factura")
    private Integer numeroFactura;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    private Long ventaId;

    private LocalDateTime fecha;
    private Boolean credito;
    private String nombre;
    private String ruc;
    private String direccion;

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

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



