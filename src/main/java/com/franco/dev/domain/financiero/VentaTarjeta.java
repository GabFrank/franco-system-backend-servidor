package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinColumnsOrFormulas;
import org.hibernate.annotations.JoinFormula;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "venta_tarjeta", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class VentaTarjeta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @SequenceGenerator(name = "financiero.venta_tarjeta_id_seq",
            sequenceName = "financiero.venta_tarjeta_id_seq",
            allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "financiero.venta_tarjeta_id_seq")
    private Long id;

    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", insertable = false, updatable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "venta_id", referencedColumnName = "id"))
    })
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "terminal_pos_id", nullable = true)
    private TerminalPos terminalPos;

    /**
     * Moneda del COBRO que este registro respalda, no la de la terminal.
     *
     * Sin esto, monto y monto_escaneado no tienen unidad: la lista los pintaba con la moneda
     * actual de la terminal, asi que cambiar esa configuracion reescribia el significado de todo
     * el historico. Y en la conciliacion, 8.000 R$ contra 8.000 Gs daba diferencia cero.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "caja_id", referencedColumnName = "id"))
    })
    private PdvCaja caja;

    @Column(name = "codigo_autorizacion")
    private String codigoAutorizacion;

    @Column(name = "numero_boleta")
    private String numeroBoleta;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "monto_escaneado", precision = 18, scale = 2)
    private BigDecimal montoEscaneado;

    @Column(name = "imagen_url")
    private String imagenUrl;

    /**
     * Campos del cupon que no son canonicos, como clave-valor.
     *
     * El mapeo del formato decide que valor extraido va a monto, codigo_autorizacion,
     * numero_boleta y terminal; todo lo demas cae aca. Asi un proveedor nuevo con campos
     * propios se resuelve desde el ABM: sin codigo, sin migracion y sin propagar a 24
     * filiales. PlugPay es el caso que lo motivo: imprime dos montos en dos monedas y cual
     * es el de la venta es configuracion, no una constante del sistema.
     */
    @Column(name = "datos_extra", columnDefinition = "jsonb")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonBinaryType")
    private String datosExtra;

    /**
     * Cadena cruda que entro por el lector cuando el registro se completo escaneando el QR del
     * cupon en el PDV. Queda NULL cuando lo completo la app movil con foto + OCR. Se guarda sin
     * normalizar: es la unica evidencia si un cupon parseo mal y el ticket termico ya se borro.
     */
    @Column(name = "qr_crudo", length = 512)
    private String qrCrudo;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    /**
     * De donde salieron los datos del cupon: QR | OCR | MANUAL | API.
     * <p>
     * No todos los origenes merecen la misma confianza: un codigo leido por OCR puede tener un
     * caracter mal --el {@code Cargo: 002511} leido {@code 802511} lo fallan los dos motores--, uno
     * tipeado por un cajero puede tener cualquier cosa, y uno que viene de la API del proveedor no
     * puede estar mal. Sin esta columna la conciliacion no puede responder cuales filas necesitan
     * que las mire una persona.
     * <p>
     * <b>Lo escribe el FILIAL, no este repo.</b> El unico metodo que pasa una venta_tarjeta a
     * COMPLETADO en los dos backends es {@code VentaTarjetaService.completar()} del filial; el
     * {@code updateVentaTarjeta} de aca es un setter generico usado para otras cosas. Central crea
     * la columna y la recibe por replicacion.
     * <p>
     * Sin CHECK en la base: central es el SUBSCRIBER de esta tabla (BRANCH_TO_MAIN), y un CHECK que
     * el publisher no comparta abortaria el apply. La validacion vive donde se escribe.
     */
    @Column(length = 20)
    private String origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
