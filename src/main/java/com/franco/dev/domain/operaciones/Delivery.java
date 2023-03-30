package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.general.Barrio;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "delivery", schema = "operaciones")
@TypeDef(
        name = "delivery_estado",
        typeClass = PostgreSQLEnumType.class
)
@IdClass(EmbebedPrimaryKey.class)
public class Delivery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "venta_id", referencedColumnName = "id"))
    })
    private Venta venta;

    @Column(name = "valor_en_gs")
    private Double valor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_id", nullable = true)
    private Funcionario entregador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barrio_id", nullable = true)
    private Barrio barrio;

    @Column(name = "vehiculo_id")
    private Integer vehiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "precio_delivery_id", nullable = true)
    private PrecioDelivery precio;

    private String telefono;

    private String direccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forma_pago_id", nullable = true)
    private FormaPago formaPago;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "vuelto_id", referencedColumnName = "id"))
    })
    private Vuelto vuelto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "delivery_estado")
    private DeliveryEstado estado;
}



