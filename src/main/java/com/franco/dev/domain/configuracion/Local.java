package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "local", schema = "configuraciones")
public class Local implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    /**
     * Nombre de la base de datos del servidor central (master). Usado en
     * replicación lógica.
     */
    @Column(name = "nombre_base_datos_master")
    private String nombreBaseDatosMaster;

    /**
     * Nombre de la base de datos del servidor filial. Usado al conectar a una
     * sucursal.
     */
    @Column(name = "nombre_base_datos_filial")
    private String nombreBaseDatosFilial;

    /**
     * IP o hostname del servidor central. Usado en replicación lógica (filiales →
     * central).
     */
    @Column(name = "ip_servidor_central")
    private String ipServidorCentral;

    /** Puerto PostgreSQL del servidor central. Usado en replicación lógica. */
    @Column(name = "puerto_servidor_central")
    private Integer puertoServidorCentral;
    @CreationTimestamp
    private Date creadoEn;

}
