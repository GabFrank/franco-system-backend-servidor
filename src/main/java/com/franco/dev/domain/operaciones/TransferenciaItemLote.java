package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.enums.EtapaAsignacionLote;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lote elegido a mano para un item de transferencia.
 *
 * Es la INTENCION del operador, no el movimiento real: el desglose efectivo sigue viviendo en
 * {@link MovimientoStockLote}. Si un item no tiene filas acá, el desglose se resuelve por FEFO
 * exactamente como antes de existir esta tabla.
 *
 * Hay varias filas por item porque una cantidad puede cubrirse con varios lotes. Es el espejo de
 * {@link RecepcionMercaderiaItemVariacion} en la entrada por compra.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "transferencia_item_lote", schema = "operaciones")
public class TransferenciaItemLote implements Serializable, Identifiable<Long> {

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

    // LAZY para no arrastrar toda la transferencia al listar las asignaciones de un item.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferencia_item_id", nullable = false)
    private TransferenciaItem transferenciaItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    /** Desnormalizado, igual que en el ledger: es inmutable y evita resolver el join para mostrar. */
    @Column(name = "numero_lote", nullable = false)
    private String numeroLote;

    private Double cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa", nullable = false)
    private EtapaAsignacionLote etapa;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
