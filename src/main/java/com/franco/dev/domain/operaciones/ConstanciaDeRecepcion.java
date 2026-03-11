package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.operaciones.enums.EstadoConstancia;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(name = "estado_constancia", typeClass = PostgreSQLEnumType.class)
@Table(name = "constancia_de_recepcion", schema = "operaciones")
public class ConstanciaDeRecepcion implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recepcion_mercaderia_id", nullable = false)
    private RecepcionMercaderia recepcionMercaderia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "codigo_verificacion", nullable = false, unique = true)
    private String codigoVerificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Type(type = "estado_constancia")
    private EstadoConstancia estado;
}

