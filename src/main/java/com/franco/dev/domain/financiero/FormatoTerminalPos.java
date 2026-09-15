package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Como se lee el ticket de un MODELO DE APARATO.
 * <p>
 * Reemplaza a {@link FormatoQrPos}, que resolvia el formato por proveedor y no distinguia una
 * maquinita Bancard de un portal web Bancard, ni dos firmwares de la misma marca. Aca un proveedor
 * tiene tantos formatos como modelos tenga, y cada terminal elige el suyo — asi conviven
 * {@code Bancard firmware v5.2} y {@code Bancard firmware v5.5} sin duplicar nada y sin reglas de
 * herencia que se rompan calladas.
 * <p>
 * Se administra SOLO en central: la tabla es MAIN_TO_ALL y un formato editable desde una sucursal
 * se desincronizaria del resto de la flota. El filial tiene la entidad espejo en modo lectura.
 *
 * @see com.franco.dev.service.financiero.FormatoTerminalPosService#save validaciones al guardar
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "formato_terminal_pos", schema = "financiero")
public class FormatoTerminalPos implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Imprime un ticket sin QR: se fotografia y lo lee el OCR. Nunca el lector. */
    public static final String TIPO_MAQUINA = "MAQUINA";
    /** Imprime un QR con los datos ya estructurados: lo lee el lector del PDV. Nunca la camara. */
    public static final String TIPO_WEB = "WEB";
    /** Los campos llegan de la API del proveedor. Entra al modelo pero el ABM todavia no lo ofrece. */
    public static final String TIPO_API = "API";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * NULL = comodin: el formato que se prueba cuando la terminal no tiene uno propio asignado.
     * Puede haber varios comodines, distinguidos por nombre.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_servicio_id", nullable = true)
    private ProveedorServicio proveedorServicio;

    /**
     * MAQUINA | WEB | API. Es el router del flujo: decide que camino se le ofrece al cajero y,
     * sobre todo, cual se le CIERRA.
     * <p>
     * String y no un enum de Java ni de PostgreSQL: {@code venta_tarjeta.estado} ya es String en
     * este modulo, y un enum de PG habria exigido un {@code ALTER TYPE} coordinado en las 24
     * filiales cada vez que aparezca un tipo nuevo. La columna lleva CHECK porque central es el
     * publisher de esta tabla; en el espejo del filial no, que es subscriber.
     */
    @Column(nullable = false, length = 20)
    private String tipo = TIPO_MAQUINA;

    /**
     * Regex con grupos nombrados (?&lt;nombre&gt;...), anclado con ^ y $.
     * <p>
     * Nullable en la base pero <b>obligatorio para MAQUINA y WEB</b>: el OCR devuelve texto igual
     * que el QR y se matchea con el mismo patron. Solo API puede no tenerlo. Lo valida
     * {@code FormatoTerminalPosService}, que puede leer el tipo y dar una frase en vez de un error
     * de Postgres.
     */
    @Column(columnDefinition = "text")
    private String patron;

    /**
     * JSON: campo destino -> {de: grupo, obligatorio: bool, mapa/escala/escalaSegunMoneda/
     * formato+zona/mayusculas}.
     * <p>
     * Los campos marcados obligatorios deciden tres cosas de una vez: que tiene que encontrar el
     * OCR para que la operacion no falle, cuando el resultado es utilizable, y que campos pide el
     * formulario de carga a mano.
     */
    @Column(nullable = false, columnDefinition = "text")
    private String mapeo;

    /** Cadena real de ejemplo. No se puede guardar un formato cuyo patron no la matchee. */
    @Column(columnDefinition = "text")
    private String ejemplo;

    /**
     * Ojo con el significado: {@code false} es <b>"no elegible para asignar a terminales
     * nuevas"</b>, no "deja de funcionar". Las terminales que ya lo tienen asignado siguen
     * operando — si desactivar un formato apagara las terminales que le apuntan, un clic en el ABM
     * dejaria sucursales enteras sin poder vender con tarjeta. Por eso el service impide
     * desactivar uno que tenga terminales asignadas.
     */
    @Column(nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /** Un formato de maquinita: el cupon se fotografia y lo lee el OCR. */
    public boolean esMaquina() {
        return TIPO_MAQUINA.equals(tipo);
    }

    /** Un formato web: el cupon trae QR y lo lee el lector del PDV. */
    public boolean esWeb() {
        return TIPO_WEB.equals(tipo);
    }

    /** Los dos tipos que leen texto y por lo tanto necesitan patron. */
    public boolean necesitaPatron() {
        return esMaquina() || esWeb();
    }
}
