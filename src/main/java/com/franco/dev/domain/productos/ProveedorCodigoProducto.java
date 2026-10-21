package com.franco.dev.domain.productos;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Lo aprendido de las facturas de un proveedor: su código interno (dCodInt) corresponde a esta presentación
 * nuestra, y cada unidad que factura son {@code factor} unidades base. Lo crea el comprador al vincular a mano
 * un renglón con «recordar».
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "proveedor_codigo_producto", schema = "productos")
public class ProveedorCodigoProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "codigo_proveedor")
    private String codigoProveedor;

    @Column(name = "descripcion_proveedor")
    private String descripcionProveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentacion_id", nullable = false)
    private Presentacion presentacion;

    private Double factor;
    private Integer usos;
    @Column(name = "ultimo_uso")
    private LocalDateTime ultimoUso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
