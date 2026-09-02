package com.franco.dev.domain.empresarial;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.enums.PerfilPapel;
import com.franco.dev.domain.empresarial.enums.TipoConexion;
import com.franco.dev.domain.empresarial.enums.TipoImpresora;
import com.franco.dev.domain.empresarial.enums.UsoImpresora;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro unico de impresoras (termicas y normales) administrable desde la app.
 * La impresora "sabe" en que sucursal/host esta conectada (campo {@link #sucursal}),
 * lo que habilita el routing filial <-> central.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "impresora", schema = "empresarial")
public class Impresora implements Identifiable<Long> {

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

    private Boolean activo;

    private Boolean esPredeterminada;

    /** Host donde esta conectada fisicamente la impresora (routing). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Enumerated(EnumType.STRING)
    private TipoImpresora tipo;

    @Enumerated(EnumType.STRING)
    private UsoImpresora uso;

    @Enumerated(EnumType.STRING)
    private TipoConexion conexion;

    /** Nombre de cola CUPS (conexion CUPS/USB/BLUETOOTH). */
    private String colaCups;

    /** Direccion IP (conexion RED / inalambrica). */
    private String ip;

    /**
     * Datos del share de Windows (conexion SMB). Son informativos/para reinstalar la cola: el
     * transporte real vive en el device-uri de la cola CUPS. La password NO se guarda aca -- esta
     * tabla se replica a TODAS las filiales via central_pub, asi que no puede llevar secretos;
     * queda solo en /etc/cups/printers.conf del host duenio.
     */
    private String smbHost;
    private String smbRecurso;
    private String smbUsuario;
    private String smbDominio;

    /** Puerto RAW/JetDirect (default 9100) para conexion RED. */
    private Integer puerto;

    @Enumerated(EnumType.STRING)
    private PerfilPapel perfilPapel;

    /** Columnas de caracteres para termicas (derivado del perfil, editable). */
    private Integer columnas;

    /** Ancho/alto en mm para hoja/custom (impresoras normales). */
    private Integer anchoMm;
    private Integer altoMm;

    /** Marca: EPSON | STAR | BEMATECH | HP | ... */
    private String marca;

    /** Code-page ESC/POS. */
    private String codepage;

    /**
     * true = ademas de vivir en {@link #sucursal}, tiene una cola CUPS proxy (IPP) instalada
     * en el central via "compartir al central". Informativo: {@link #sucursal} sigue siendo el
     * host real para el routing de {@code PrintRouterService}.
     */
    private Boolean compartidaEnCentral;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
}
