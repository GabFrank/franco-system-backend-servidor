package com.franco.dev.domain.administrativo;

import com.franco.dev.domain.administrativo.enums.MetodoMarcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.franco.dev.domain.EmbebedPrimaryKey;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "marcacion", schema = "administrativo")
@AllArgsConstructor
@NoArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
@IdClass(EmbebedPrimaryKey.class)
public class Marcacion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @Id
    @Column(name = "sucursal_id")
    private Long sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_marcacion", columnDefinition = "administrativo.tipo_marcacion")
    @Type(type = "pgsql_enum")
    private TipoMarcacion tipo;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(name = "precision_gps")
    private Float precisionGps;

    @Column(name = "distancia_sucursal")
    private Integer distanciaSucursalMetros;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_info")
    private String deviceInfo;

    /**
     * Como se identifico a la persona. Ver {@link MetodoMarcacion}.
     *
     * Es VARCHAR y no un enum de Postgres --a diferencia de `tipo_marcacion`-- porque la
     * tabla se replica a las filiales y un tipo nuevo habria que crearlo en cada una
     * antes de que llegue la primera fila.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_registro", length = 30)
    private MetodoMarcacion metodoRegistro;

    /** Similitud del match aceptado, 0..1. Null si no hubo rostro. */
    @Column(name = "similitud_facial")
    private Float similitudFacial;

    /**
     * Cuanto le saco el reconocido al segundo candidato.
     *
     * Null cuando no hubo segundo --un solo enrolado-- o cuando no fue 1:N. No se rellena
     * con 0 ni con 1: inventar la medicion es peor que no tenerla.
     */
    @Column(name = "margen_segundo_candidato")
    private Float margenSegundoCandidato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_entrada_id")
    private Sucursal sucursalEntrada;

    @Column(name = "fecha_entrada")
    private LocalDateTime fechaEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_salida_id")
    private Sucursal sucursalSalida;

    @Column(name = "fecha_salida")
    private LocalDateTime fechaSalida;

    private Boolean presencial;

    private Long autorizacion;

    private String codigo;

    @Transient
    private Boolean esSalidaAlmuerzo;
}
