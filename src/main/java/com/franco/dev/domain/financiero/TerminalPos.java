package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "terminal_pos", schema = "financiero")
public class TerminalPos implements Identifiable<Long> {

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

    private String descripcion;

    /**
     * La etiqueta interna que el negocio le pega al aparato para que el cajero la escanee con el
     * lector ({@code scan-terminal-pos-dialog}).
     * <p>
     * <b>No es el identificador de la maquina</b> — ese es {@link #serie}. Son dos cosas con dos
     * vidas: {@code codigo} lo elige el negocio y sirve para seleccionar la terminal en la caja;
     * {@code serie} viene de fabrica y es lo que el cupon imprime.
     */
    private String codigo;

    /**
     * Donde esta fisicamente el aparato.
     * <p>
     * NULL en las filas viejas y sin backfill: no se puede adivinar en que local esta una maquina,
     * se completa a mano. Con 24 sucursales y un proveedor que entrega 30 maquinas, esta columna es
     * lo unico que responde "cuantas maquinas deberia tener mi local".
     * <p>
     * <b>La replicacion NO se filtra por esto.</b> {@code terminal_pos} es MAIN_TO_ALL sin filtro y
     * se deja asi: prender el filtro haria que una terminal sin sucursal asignada deje de bajar a
     * las filiales, y si alguna caja dependia de ella para cobrar, se queda sin cobrar. El caso de
     * uso es de listado, no de aislamiento, y se resuelve con un WHERE en la consulta.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    /**
     * El identificador propio de la maquina: el que viene de fabrica y el que el cupon imprime.
     * <p>
     * El {@code mapeo} del formato ya declara {@code terminal} como campo canonico, o sea que el
     * cupon ya trae este dato. Hasta ahora no habia contra que cotejarlo. Con la serie cargada, un
     * cupon dice solo de que maquina salio — y si esa maquina esta registrada en otra sucursal, el
     * sistema lo puede cantar.
     * <p>
     * Se guarda normalizada (trim + mayusculas) por {@code TerminalPosService}: los dos indices
     * unicos parciales de {@code V224.5} comparan la columna cruda, asi que sin normalizar
     * {@code jf798sjj} y {@code JF798SJJ} serian dos maquinas distintas.
     */
    @Column(length = 60)
    private String serie;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_bancaria_id", nullable = true)
    private CuentaBancaria cuentaBancaria;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @Column(name = "porcentaje_comision")
    private java.math.BigDecimal porcentajeComision;

    @Column(name = "minutos_acreditacion")
    private Integer minutosAcreditacion;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_servicio_id", nullable = true)
    private ProveedorServicio proveedorServicio;

    /**
     * Formato del modelo de aparato que es esta terminal: de aca sale el tipo (MAQUINA / WEB /
     * API), el patron y el mapeo.
     * <p>
     * NULL = sin configurar. El dia del corte lo estan TODAS las terminales de las 24 sucursales
     * --no hay backfill, la asignacion se completa a mano por SQL-- y el desktop bloquea la venta
     * con tarjeta mientras siga asi. Por eso ese SQL es prerrequisito de liberar la version del
     * desktop, no una tarea posterior.
     * <p>
     * Aca la FK SI existe en la base (V221.5): las dos tablas se escriben desde el mismo ABM, en la
     * misma base, sin carrera posible. En el espejo del filial no, porque alla bajan por streams de
     * replicacion distintos y sin orden garantizado entre ellos.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "formato_terminal_pos_id", nullable = true)
    private FormatoTerminalPos formatoTerminalPos;

    /**
     * Si en esta terminal se puede tipear el cupon a mano.
     * <p>
     * Tres estados, no dos: {@code null} = hereda la configuracion general del modulo,
     * {@code true}/{@code false} = decidido para este aparato. Hay cajas donde tipear es aceptable
     * y otras donde el cajero tiene el lector al lado y tipear es la puerta de entrada al error.
     * <p>
     * <b>No puede apagar el ultimo camino.</b> El tipo del formato ya cierra el camino que no
     * corresponde --WEB no ofrece camara, MAQUINA no ofrece lector-- y eso solo es seguro porque la
     * carga a mano es la salida universal. Apagarla en una terminal cuyo otro camino tampoco esta
     * abierto deja a la caja sin ninguna forma de cobrar con tarjeta, sin que nadie avise: venta
     * PENDIENTE y caja que no cierra. Lo valida {@code TerminalPosService}.
     */
    @Column(name = "carga_manual_permitida")
    private Boolean cargaManualPermitida;

    /**
     * JSON array con los campos que no se pueden dejar vacios al registrar la venta de este
     * aparato. {@code null} = se deduce del {@code mapeo} del formato.
     * <p>
     * <b>Solo puede apretar.</b> La lista tiene que contener todos los que el mapeo del formato ya
     * declara obligatorios: si pudiera aflojarlos, la configuracion por POS seria una forma de
     * saltear la validacion del formato desde una pantalla que parece menor.
     */
    @Column(name = "campos_obligatorios", columnDefinition = "text")
    private String camposObligatorios;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
