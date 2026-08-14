package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Serie de numeración correlativa de comprobantes internos (CN3). Una serie por
 * tipo de comprobante; {@code siguiente} se incrementa atómicamente al emitir.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "comprobante_serie", schema = "financiero")
public class ComprobanteSerie implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    /** GASTO | ENTRADA_VARIA | OPERACION_FINANCIERA | RECIBO_COBRO | RECIBO_PAGO | RETIRO ... */
    private String tipo;

    private String prefijo;

    private Long siguiente;

    @Column(name = "relleno_ceros")
    private Integer rellenoCeros;

    private Boolean activo;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
