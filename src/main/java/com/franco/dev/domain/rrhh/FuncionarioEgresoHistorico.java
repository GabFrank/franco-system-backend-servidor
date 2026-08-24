package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot del estado que un egreso destruye, tomado ANTES de guardarlo.
 *
 * <p>Egresar no solo apaga tres campos: {@code FuncionarioService.save} pone el credito
 * del funcionario en cero, y la cascada de estado inactiva al usuario, inactiva al
 * cliente, lo devuelve a NORMAL y le borra el credito. Sin esta foto, revertir un egreso
 * hecho por error obliga a ir a un backup -- que es lo que paso en farmacia el
 * 2026-08-21.</p>
 *
 * <p>Con el snapshot la reversa <b>restaura</b> en vez de preguntar, y el cliente
 * recupera su tipo real: sin {@code clienteTipoAnterior} la cascada lo reactiva siempre
 * como FUNCIONARIO, degradando en silencio a un VIP.</p>
 *
 * <p>Aditivo y central-only, como las otras dos historicas del modulo.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "funcionario_egreso_historico", schema = "rrhh")
public class FuncionarioEgresoHistorico implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id", nullable = true)
    private Funcionario funcionario;

    @Column(name = "fecha_egreso")
    private LocalDateTime fechaEgreso;

    @Column(name = "motivo_egreso")
    private String motivoEgreso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "egresado_por_id", nullable = true)
    private Usuario egresadoPor;

    /** Credito del funcionario antes del egreso. save() lo pone en cero. */
    @Column(name = "credito_anterior")
    private BigDecimal creditoAnterior;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_activo_anterior")
    private Boolean usuarioActivoAnterior;

    @Column(name = "cliente_id")
    private Long clienteId;

    /** Tipo del cliente antes del egreso. Sin esto, revertir degrada a un VIP. */
    @Column(name = "cliente_tipo_anterior")
    private String clienteTipoAnterior;

    @Column(name = "cliente_credito_anterior")
    private BigDecimal clienteCreditoAnterior;

    @Column(name = "cliente_activo_anterior")
    private Boolean clienteActivoAnterior;

    /** NULL mientras el egreso siga vigente. */
    @Column(name = "revertido_en")
    private LocalDateTime revertidoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revertido_por_id", nullable = true)
    private Usuario revertidoPor;

    @Column(name = "motivo_reversion")
    private String motivoReversion;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
