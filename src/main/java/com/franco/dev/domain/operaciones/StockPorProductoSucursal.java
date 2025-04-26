package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "stock_por_producto_sucursal", schema = "operaciones")
@IdClass(EmbebedPrimaryKey.class)
public class StockPorProductoSucursal {

    @Id
    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Id
    @Column(name = "producto_id")
    private Long id;

    private Long lastMovimientoStockId;

    private Double cantidad;

    public Double sumarCantidad(Double aux){
        Double newCantidad = cantidad + aux;
        cantidad = newCantidad;
        return cantidad;
    }
}