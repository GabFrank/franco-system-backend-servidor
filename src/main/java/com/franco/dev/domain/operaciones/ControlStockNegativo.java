package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.operaciones.enums.TipoControlStock;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Salida de un producto cuyo stock en la sucursal ya era 0 o negativo. Central-only.
 *
 * Entidad de SOLO LECTURA: nadie la persiste por JPA. Las filas VENTA las inserta el poller y las
 * TRANSFERENCIA las inserta ControlStockNegativoService, las dos por SQL.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "control_stock_negativo", schema = "operaciones")
public class ControlStockNegativo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoControlStock tipo;

    private Double cantidad;

    @Column(name = "stock_previo")
    private Double stockPrevio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime fecha;

    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "movimiento_stock_id")
    private Long movimientoStockId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
