package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.ResultadoVerificacionRetiro;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que tesorería contó al recibir un retiro de PDV.
 *
 * El retiro del PDV es inmutable: es la declaración del origen y es la evidencia. Esta entidad
 * es un documento aparte, central-only, que registra qué se contó de verdad. A la caja mayor
 * entra <b>lo contado</b>, no lo declarado.
 *
 * No se replica a las filiales (no lleva fila en {@code configuraciones.replication_table}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "retiro_verificacion", schema = "financiero")
public class RetiroVerificacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mitad del retiro verificado. La PK del retiro es compuesta: hace falta también la sucursal. */
    @Column(name = "retiro_id", nullable = false)
    private Long retiroId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    /** Quién contó. Importa: el que cuenta no puede ser después el que investiga. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", length = 20, nullable = false)
    private ResultadoVerificacionRetiro resultado;

    /**
     * Se confirmó lo declarado sin contar por denominación. Queda marcado a propósito: si más
     * adelante aparece una diferencia, hay que poder saber que ese retiro nunca se contó
     * billete por billete.
     */
    @Column(name = "rapida", nullable = false)
    private Boolean rapida = false;

    @Column(name = "observacion")
    private String observacion;

    /** Una verificación anulada libera el índice único y deja hacer otra sobre el mismo retiro. */
    @Column(name = "anulada", nullable = false)
    private Boolean anulada = false;

    @OneToMany(mappedBy = "verificacion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RetiroVerificacionDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(RetiroVerificacionDetalle d) {
        d.setVerificacion(this);
        this.detalles.add(d);
    }
}
