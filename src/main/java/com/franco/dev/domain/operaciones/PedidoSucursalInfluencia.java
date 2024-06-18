package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pedido_sucursal_influencia", schema = "operaciones")
public class PedidoSucursalInfluencia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    // Assuming you have corresponding entities for Usuario, Sucursal, and Pedido
    @ManyToOne( fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne( fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    @ManyToOne( fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id", nullable = true)
    private Pedido pedido;

}

