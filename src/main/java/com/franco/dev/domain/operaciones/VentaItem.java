package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "venta_item", schema = "operaciones")
@TypeDef(
        name = "unidad_medida",
        typeClass = PostgreSQLEnumType.class
)
@IdClass(EmbebedPrimaryKey.class)
public class VentaItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "venta_id", referencedColumnName = "id"))
    })
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_id", nullable = true)
    private Presentacion presentacion;

    private Double cantidad;

    private Double existencia;

    @Column(name = "costo_unitario")
    private Double precioCosto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precio_id", nullable = true)
    private PrecioPorSucursal precioVenta;

    private Double precio;

    @Column(name = "descuento_unitario")
    private Double valorDescuento;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida")
    @Type(type = "unidad_medida")
    private UnidadMedida unidadMedida;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean activo;


}