package com.franco.dev.domain.financiero;

import com.franco.dev.domain.personas.ProveedorServicio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Como se lee el QR que imprime la maquinita de un proveedor de servicio.
 * <p>
 * Existe porque no todos los proveedores pueden cambiar el formato que imprimen: a ValidaPix le
 * pedimos el nuestro (FRCP1) y acepto, pero al resto habra que adaptarse. Escribir cada formato
 * en el codigo obligaria a un release por proveedor; asi se carga desde la pantalla y baja a
 * todas las filiales por replicacion MAIN_TO_ALL.
 * <p>
 * Se administra SOLO en central. El filial tiene la entidad espejo en modo lectura.
 *
 * @see com.franco.dev.service.financiero.FormatoQrPosService#save validaciones al guardar
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "formato_qr_pos", schema = "financiero")
public class FormatoQrPos implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * NULL = comodin: se prueba cuando la terminal escaneada no tiene proveedor asignado, o
     * cuando su proveedor no tiene formato propio. Puede haber varios comodines.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_servicio_id", nullable = true)
    private ProveedorServicio proveedorServicio;

    /** Regex con grupos nombrados (?&lt;nombre&gt;...), anclado con ^ y $. */
    @Column(nullable = false, columnDefinition = "text")
    private String patron;

    /** JSON: campo destino -> {de: grupo, mapa/escala/escalaSegunMoneda/formato+zona/mayusculas}. */
    @Column(nullable = false, columnDefinition = "text")
    private String mapeo;

    /** Cadena real de ejemplo. No se puede guardar un formato cuyo patron no la matchee. */
    @Column(nullable = false, columnDefinition = "text")
    private String ejemplo;

    @Column(nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;
}
