package com.franco.dev.domain.financiero;

import com.franco.dev.domain.financiero.enums.EstadoCasoRetiro;
import com.franco.dev.domain.financiero.enums.VeredictoCasoRetiro;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Un retiro que no cerró y hay que investigar.
 *
 * Existe separado de la verificación a propósito: <b>el que recibe no investiga</b>. Tesorería
 * cuenta, registra y abre el caso; otro lo toma y lo cierra. Si la diferencia quedara solo como
 * una observación dentro de la verificación, nadie la miraría — que es exactamente lo que pasa
 * hoy con {@code retiro.observacion}.
 *
 * No lleva categoría propia: esa vive en el detalle de la verificación, por moneda, porque un
 * mismo retiro puede ser faltante en una y sobrante en otra. El caso agrupa.
 *
 * Central-only, no replicado.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "retiro_caso", schema = "financiero")
public class RetiroCaso implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retiro_id", nullable = false)
    private Long retiroId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificacion_id")
    private RetiroVerificacion verificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoCasoRetiro estado = EstadoCasoRetiro.ABIERTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abierto_por")
    private Usuario abiertoPor;

    /** No puede ser el mismo que contó: quien verifica también puede ser el problema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_a")
    private Usuario asignadoA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por")
    private Usuario resueltoPor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "resolucion")
    private String resolucion;

    /**
     * Qué se determinó. Se llena al resolver: un caso abierto no tiene veredicto, y esa es la
     * diferencia entre "hay una diferencia" y "sabemos qué pasó".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "veredicto", length = 40)
    private VeredictoCasoRetiro veredicto;

    /**
     * A quién se le atribuye. Apunta a persona y no a funcionario ni a usuario porque el
     * responsable puede estar de cualquiera de los dos lados — el cajero del PDV o el que contó
     * en tesorería — y persona es lo único que los dos comparten.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_persona_id")
    private Persona responsablePersona;

    /**
     * Solo con veredicto REINTEGRADO: el retiro por el que volvió la plata.
     *
     * Lleva la sucursal al lado porque el id de retiro no es único entre filiales: sin ella,
     * el número no identifica ningún documento.
     */
    @Column(name = "reintegro_retiro_id")
    private Long reintegroRetiroId;

    @Column(name = "reintegro_sucursal_id")
    private Long reintegroSucursalId;
}
