package com.franco.dev.domain.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "caja_virtual", schema = "financiero")
@TypeDef(name = "caja_virtual_tipo", typeClass = PostgreSQLEnumType.class)
public class CajaVirtual implements Serializable {

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

    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    @Type(type = "caja_virtual_tipo")
    private CajaVirtualTipo tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id", nullable = true)
    private Funcionario responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "saldo_gs")
    private Double saldoGs;

    @Column(name = "saldo_rs")
    private Double saldoRs;

    @Column(name = "saldo_ds")
    private Double saldoDs;

    @Column(name = "limite_gs")
    private Double limiteGs;

    /** Si es false, un egreso/ajuste que dejaría el saldo por debajo de 0 se rechaza (CN2). */
    @Column(name = "permite_saldo_negativo")
    private Boolean permiteSaldoNegativo;

    private String descripcion;

    private Boolean activo;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
