package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "inventario_producto", schema = "operaciones")
public class InventarioProducto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long idCentral;

    private Long idOrigen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventario_id", nullable = true)
    private Inventario inventario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = true)
    private Zona zona;

    private Boolean concluido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}