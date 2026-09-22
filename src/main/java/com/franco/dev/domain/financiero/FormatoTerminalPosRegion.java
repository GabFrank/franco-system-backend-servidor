package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila del mapa de un formato: que caja del OCR es que campo.
 *
 * <p><b>Anclada a la etiqueta impresa, no a coordenadas absolutas.</b> Es la regla mas importante
 * del diseno y la mas facil de perder al implementar. Un mapa por coordenadas se rompe el dia que
 * el proveedor agrega una linea al ticket, y se rompen TODOS los mapas de ese modelo a la vez sin
 * que nadie entienda por que. Anclado a la etiqueta sobrevive: si {@code AUT:} se corrio 20px para
 * abajo, el valor sigue estando a su derecha.
 *
 * <p>La geometria ({@link #x1}..{@link #y2}) esta igual, pero como <b>pista para acotar el
 * reconocimiento</b>, no como verdad para asignar. La geometria achica el trabajo --reconocer 6
 * cajas en vez de 26 baja {@code rec} de 3.841 a ~900 ms--, la etiqueta decide de quien es cada
 * caja.
 *
 * <p><b>Cuelgan del formato, no de la terminal.</b> El doc de dominio decia "por POS" porque se
 * escribio antes de que el formato pasara a ser del modelo de aparato. Ahora dos cajas con la misma
 * maquinita comparten el mapa en vez de dibujarlo dos veces, que era el problema real que "por POS"
 * venia a resolver.
 *
 * <p>Se administra SOLO en central: la tabla es MAIN_TO_ALL y el filial tiene el espejo en modo
 * lectura, del que saca las zonas para acotar el OCR.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "formato_terminal_pos_region", schema = "financiero")
public class FormatoTerminalPosRegion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** El valor esta a la derecha de la etiqueta, en el mismo renglon. */
    public static final String POSICION_DERECHA = "DERECHA";
    /** El valor esta en el renglon de abajo. */
    public static final String POSICION_ABAJO = "ABAJO";
    /** Etiqueta y valor salieron en la misma caja: "AUT: 883921". */
    public static final String POSICION_DENTRO = "DENTRO";

    public static final String TIPO_TEXTO = "TEXTO";
    public static final String TIPO_NUMERO = "NUMERO";
    public static final String TIPO_FECHA = "FECHA";

    /** Salio de un cupon de muestra, por construccion. La derivacion la puede volver a pisar. */
    public static final String ORIGEN_DERIVADA = "DERIVADA";
    /** La corrigio una persona mirando un cupon. Ninguna corrida de derivacion la toca. */
    public static final String ORIGEN_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "formato_terminal_pos_id", nullable = false)
    private FormatoTerminalPos formatoTerminalPos;

    /**
     * El nombre del campo destino, en camelCase y tal como aparece como <b>clave del mapeo</b> del
     * formato: {@code codigoAutorizacion}, {@code numeroBoleta}, {@code monto}, {@code terminal},
     * {@code identificadorTransaccion}, {@code moneda}, o una clave libre que termina en
     * {@code venta_tarjeta.datos_extra}.
     * <p>
     * Tiene que coincidir con el mapeo, y el service lo valida: una region para un campo que el
     * mapeo no produce no solo es peso muerto, ademas <b>acota el reconocimiento a una zona para
     * nada</b>.
     */
    @Column(nullable = false, length = 40)
    private String campo;

    /**
     * La etiqueta impresa que ancla la region: {@code AUT:}, {@code MONTO}, {@code TERMINAL}.
     * <p>
     * NULL = sin etiqueta a la vista. La region se resuelve solo por geometria, y por lo tanto es
     * la fragil ante un cambio de largo del ticket. La derivacion lo marca asi a proposito, para
     * que el administrador sepa cual mirar primero cuando un formato deje de reconocer.
     */
    @Column(length = 120)
    private String etiqueta;

    /** DERECHA | ABAJO | DENTRO. String y no enum: ver el comentario de {@code tipo} mas abajo. */
    @Column(length = 20)
    private String posicion;

    /**
     * TEXTO | NUMERO | FECHA. Un campo declarado NUMERO rechaza un {@code 0i64} del OCR gratis, que
     * es justo el tipo de error que ni Java ni Python evitan solos.
     * <p>
     * String y no un enum de Java: en este repo un valor nuevo en un enum tiene que ir tambien al
     * {@code .graphqls} en el mismo commit o revienta en runtime al serializar --es la falla mas
     * silenciosa del proyecto--. El modulo entero de venta con tarjeta ya usa String por eso mismo.
     */
    @Column(length = 20)
    private String tipo;

    /** Si el campo no se lee, la operacion falla en vez de aceptarse a medias y en silencio. */
    @Column(nullable = false)
    private Boolean obligatorio = Boolean.FALSE;

    /** Pista geometrica normalizada 0..1. Las cuatro o ninguna: media caja no es una pista. */
    @Column(precision = 6, scale = 5)
    private BigDecimal x1;

    @Column(precision = 6, scale = 5)
    private BigDecimal y1;

    @Column(precision = 6, scale = 5)
    private BigDecimal x2;

    @Column(precision = 6, scale = 5)
    private BigDecimal y2;

    /**
     * DERIVADA | MANUAL.
     * <p>
     * No es decorativo: decide si una corrida de derivacion la puede pisar. Una region MANUAL es el
     * artefacto mas caro del modulo --alguien miro un cupon y la arreglo-- y la derivacion es
     * repetible, se dispara como accion del usuario. Sin esta distincion, un clic borraria la
     * correccion, y encima no quedaria local: la tabla baja a las 24 filiales.
     */
    @Column(nullable = false, length = 20)
    private String origen = ORIGEN_DERIVADA;

    @Column(nullable = false)
    private Integer orden = 0;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    public boolean esManual() {
        return ORIGEN_MANUAL.equals(origen);
    }

    /** Si trae pista geometrica. Las cuatro coordenadas o ninguna, lo garantiza el CHECK. */
    public boolean tieneCaja() {
        return x1 != null && y1 != null && x2 != null && y2 != null;
    }
}
