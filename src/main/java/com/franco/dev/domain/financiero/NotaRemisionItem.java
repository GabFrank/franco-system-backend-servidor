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
 * Ítem de una nota de remisión: qué se traslada y cuánto. **Sin precio ni IVA** — la NRE no lleva
 * valores (no se arma gValorItem ni gCamIVA, y el DE no lleva gTotSub).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "nota_remision_item", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class NotaRemisionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * El id se toma de la secuencia en el service, no con @GeneratedValue: con @IdClass,
     * Hibernate intenta escribir POST_INSERT_INDICATOR en el campo de la clase de la PK y el
     * INSERT falla ("Could not set field value [POST_INSERT_INDICATOR]"). Es la misma razon por
     * la que en central nunca se inserto una factura ni un documento electronico a mano.
     */
    @Id
    @Column(name = "id")
    private Long id;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "nota_remision_id", nullable = false)
    private Long notaRemisionId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "presentacion_id")
    private Long presentacionId;

    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal cantidad;

    /** Código de unidad de medida de SIFEN (cUniMed); se precarga de factura_legal_item. */
    @Column(name = "unidad_medida")
    private String unidadMedida;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
