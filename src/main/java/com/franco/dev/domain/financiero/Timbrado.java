package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import javax.validation.constraints.AssertTrue;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "timbrado", schema = "financiero")
public class Timbrado implements Identifiable<Long> {

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

    private String razonSocial;

    private String ruc;

    private String numero;

    @Column(name = "is_electronico")
    private Boolean isElectronico;

    private String csc;

    @Column(name = "csc_id")
    private String cscId;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    // Campos para documento electrónico
    private String email;
    
    @Column(name = "tipo_sociedad")
    private String tipoSociedad;
    
    @Column(name = "domicilio_fiscal_departamento")
    private String domicilioFiscalDepartamento;
    
    @Column(name = "domicilio_fiscal_ciudad")
    private String domicilioFiscalCiudad;
    
    @Column(name = "domicilio_fiscal_codigo_ciudad")
    private String domicilioFiscalCodigoCiudad;
    
    @Column(name = "domicilio_fiscal_localidad")
    private String domicilioFiscalLocalidad;
    
    @Column(name = "domicilio_fiscal_barrio")
    private String domicilioFiscalBarrio;
    
    @Column(name = "domicilio_fiscal_direccion")
    private String domicilioFiscalDireccion;
    
    private String telefono;
    
    @Column(name = "cod_actividad_economica_principal")
    private String codActividadEconomicaPrincipal;
    
    @Column(name = "desc_actividad_economica_principal")
    private String descActividadEconomicaPrincipal;
    
    @Column(name = "list_codigo_actividad_economica_secundaria")
    private String listCodigoActividadEconomicaSecundaria;
    
    @Column(name = "list_descripcion_actividad_economica_secundaria")
    private String listDescripcionActividadEconomicaSecundaria;

    private Boolean activo;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    /**
     * Validación personalizada: Las fechas son obligatorias SOLO para timbrados físicos (no electrónicos)
     */
    @AssertTrue(message = "Las fechas de inicio y fin son obligatorias para timbrados físicos")
    public boolean isValidDateRequirement() {
        // Si es electrónico, las fechas no son obligatorias
        if (Boolean.TRUE.equals(isElectronico)) {
            return true;
        }
        
        // Si no es electrónico (físico), las fechas SÍ son obligatorias
        return fechaInicio != null && fechaFin != null;
    }
}



