package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.AccionChequeInutilizado;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Un número de chequera que no se usa (issue #603). Vigente mientras {@code revertidoEn} sea null. */
@Data
@Entity
@Table(name = "chequera_numero_inutilizado", schema = "financiero")
public class ChequeraNumeroInutilizado implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chequera_id", nullable = false)
    private Chequera chequera;

    private Long numero;

    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cheque_id")
    private Cheque cheque;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion_cheque")
    private AccionChequeInutilizado accionCheque = AccionChequeInutilizado.NINGUNA;

    @Column(name = "numero_nuevo")
    private Long numeroNuevo;

    @Column(name = "revertido_en")
    private LocalDateTime revertidoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revertido_por_id")
    private Usuario revertidoPor;
}
