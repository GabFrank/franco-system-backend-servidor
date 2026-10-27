package com.franco.dev.domain.financiero;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * "Desde {@code vigenteDesde}, el modulo X usa la moneda Y" (V258.5). Una fila nueva reemplaza a la
 * anterior de su clave (modulo + sucursal) y ninguna se edita salvo {@code activo}: el historial es
 * la tabla misma. Se replica MAIN_TO_ALL. La resuelve {@code MonedaModuloLector}; sin filas, cada
 * modulo usa la moneda de la empresa.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "configuracion_moneda_modulo", schema = "financiero")
public class ConfiguracionMonedaModulo implements Identifiable<Long> {

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "modulo", nullable = false, length = 20)
    private ModuloMoneda modulo;

    /** NULL = toda la empresa. Solo el PDV admite una fila por sucursal (CHECK de V258.5). */
    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @ManyToOne
    @JoinColumn(name = "moneda_id", nullable = false)
    private Moneda moneda;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    /** false = se ignora y rige la anterior activa de la misma clave. Es la vuelta atras. */
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /** Lo unico del autor que expone el .graphqls: el tipo Usuario completo trae el password. */
    public String getUsuarioNickname() {
        return usuario != null ? usuario.getNickname() : null;
    }
}
