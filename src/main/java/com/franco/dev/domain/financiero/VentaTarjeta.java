package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
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
     * Cadena cruda que entro por el lector cuando el registro se completo escaneando el QR del
     * cupon en el PDV. Queda NULL cuando lo completo la app movil con foto + OCR. Se guarda sin
     * normalizar: es la unica evidencia si un cupon parseo mal y el ticket termico ya se borro.
     */
    @Column(name = "qr_crudo", length = 512)
    private String qrCrudo;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
