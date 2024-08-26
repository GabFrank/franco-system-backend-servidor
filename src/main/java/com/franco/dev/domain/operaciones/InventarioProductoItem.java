package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.domain.operaciones.enums.InventarioProductoEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "inventario_producto_item", schema = "operaciones")
@TypeDef(
        name = "inventario_producto_estado",
        typeClass = PostgreSQLEnumType.class
)
public class InventarioProductoItem implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventario_producto_id", nullable = true)
    private InventarioProducto inventarioProducto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "presentacion_id", nullable = true)
    private Presentacion presentacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = true)
    private Zona zona;

    private Double cantidad;

    private Double cantidadFisica;

    @JoinColumn(name = "vencimiento", nullable = true)
    private LocalDateTime vencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    @Type(type = "inventario_producto_estado")
    private InventarioProductoEstado estado;

    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    public LocalDateTime getVenticimiento(){
        return vencimiento;
    }

    public void setVencimiento(LocalDateTime v){
        vencimiento = v;
    }
}