package com.franco.dev.domain.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "cambio_caja", schema = "financiero")
@IdClass(EmbebedPrimaryKey.class)
public class CambioCaja implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Id
    @Column(name = "sucursal_id", insertable = false, updatable = false)
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizado_por_id", nullable = true)
    private Funcionario autorizadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_venta_id", nullable = true)
    private Moneda monedaVenta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_compra_id", nullable = true)
    private Moneda monedaCompra;

    private Double cotizacion;

    private String observacion;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}



