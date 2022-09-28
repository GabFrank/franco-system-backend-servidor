package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.EmbeddedEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinColumnsOrFormulas;
import org.hibernate.annotations.JoinFormula;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "gasto", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class Gasto extends EmbeddedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id", nullable = true)
    private Funcionario responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumnsOrFormulas(value = {
            @JoinColumnOrFormula(formula = @JoinFormula(value = "sucursal_id", referencedColumnName = "sucursal_id")),
            @JoinColumnOrFormula(column = @JoinColumn(name = "caja_id", referencedColumnName = "id"))
    })
    private PdvCaja caja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_gasto_id", nullable = true)
    private TipoGasto tipoGasto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Funcionario autorizadoPor;

    private String observacion;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    private Boolean activo;

    private Boolean finalizado;

    private Double retiroGs;
    private Double retiroRs;
    private Double retiroDs;
    private Double vueltoGs;
    private Double vueltoRs;
    private Double vueltoDs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_vuelto_id", nullable = true)
    private Sucursal sucursalVuelto;
}



