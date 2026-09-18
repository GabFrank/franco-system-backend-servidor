package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ítem de una nota de crédito: copia del ítem de la factura. A diferencia de la remisión, **sí**
 * lleva precio e IVA — la NC mueve dinero.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nota_credito_item", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class NotaCreditoItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "nota_credito_id", nullable = false)
    private Long notaCreditoId;

    /** De qué ítem de la factura sale, para poder auditar la correspondencia 1:1. */
    @Column(name = "factura_legal_item_id")
    private Long facturaLegalItemId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "presentacion_id")
    private Long presentacionId;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal cantidad;

    @Column(name = "unidad_medida")
    private String unidadMedida;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private BigDecimal total;

    /** Tasa de IVA del ítem (0, 5 o 10). */
    private Integer iva;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
