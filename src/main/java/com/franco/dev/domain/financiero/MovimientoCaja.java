package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "movimiento_caja", schema = "financiero")
@TypeDef(
        name = "pdv_caja_tipo_movimiento",
        typeClass = PostgreSQLEnumType.class
)
@IdClass(EmbebedPrimaryKey.class)
public class MovimientoCaja implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @Column(name = "referencia_id")
    private Long referencia;
    private Double cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id", nullable = true)
    private Moneda moneda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cambio_id", nullable = true)
    private Cambio cambio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "caja_id", referencedColumnName = "id"))
    })
    private PdvCaja pdvCaja;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento")
    @Type(type = "pdv_caja_tipo_movimiento")
    private PdvCajaTipoMovimiento tipoMovimiento;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean activo;
}



