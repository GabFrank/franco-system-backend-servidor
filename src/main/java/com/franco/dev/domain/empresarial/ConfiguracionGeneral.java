package com.franco.dev.domain.empresarial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.franco.dev.domain.personas.Usuario;
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
@Table(name = "configuracion_general", schema = "empresarial")
public class ConfiguracionGeneral implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreEmpresa;

    private String razonSocial;

    private String ruc;

    @Column(name = "timbrado_numero")
    private String timbradoNumero;

    @Column(name = "timbrado_vigencia_hasta")
    private java.time.LocalDate timbradoVigenciaHasta;

    @Column(name = "punto_expedicion")
    private String puntoExpedicion;

    @Column(name = "zona_horaria")
    private String zonaHoraria;

    @Column(name = "moneda_principal_id")
    private Long monedaPrincipalId;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "actividad_economica")
    private String actividadEconomica;

    /** CN4: días máximos hacia atrás para anular un movimiento de caja (null = sin límite). */
    @Column(name = "dias_limite_anulacion")
    private Integer diasLimiteAnulacion;

    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private Usuario usuario;

}