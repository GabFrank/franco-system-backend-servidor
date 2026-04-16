package com.franco.dev.domain.activos;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Proveedor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vehiculo", schema = "vehiculos")
public class Vehiculo implements Identifiable<Long> {

        private static final long serialVersionUID = 1L;

        @Id
        @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
        @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "modelo_id", nullable = true)
        @NotFound(action = NotFoundAction.IGNORE)
        private Modelo modelo;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "tipo_vehiculo", nullable = true)
        @NotFound(action = NotFoundAction.IGNORE)
        private TipoVehiculo tipoVehiculo;

        private String chapa;

        private String color;

        @Column(name = "anho")
        private Integer anho;

        private Boolean documentacion;

        private Boolean refrigerado;

        private Boolean nuevo;

        @Column(name = "fecha_adquisicion")
        private LocalDate fechaAdquisicion;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "propietario_id")
        private com.franco.dev.domain.personas.Persona propietario;

        @Column(name = "identificador_interno")
        private String identificadorInterno;

        @Column(name = "valor_estimado")
        private BigDecimal valorEstimado;

        @Column(name = "valor_estimado_pyg")
        private BigDecimal valorEstimadoPyg;

        @Column(name = "valor_estimado_brl")
        private BigDecimal valorEstimadoBrl;

        @OneToOne(mappedBy = "vehiculo", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
        private VehiculoEspecificaciones especificaciones;

        @OneToOne(mappedBy = "vehiculo", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
        private VehiculoAdjuntos adjuntos;

        @OneToOne(mappedBy = "vehiculo", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
        private VehiculoFinanzas finanzas;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "usuario_id", nullable = true)
        private Usuario usuario;

        @Column(name = "creado_en")
        private LocalDateTime creadoEn;

        @Transient
        public BigDecimal getPrimerKilometraje() {
                return especificaciones != null ? especificaciones.getPrimerKilometraje() : null;
        }

        public void setPrimerKilometraje(BigDecimal primerKilometraje) {
                getOrCreateEspecificaciones().setPrimerKilometraje(primerKilometraje);
        }

        @Transient
        public BigDecimal getCapacidadKg() {
                return especificaciones != null ? especificaciones.getCapacidadKg() : null;
        }

        public void setCapacidadKg(BigDecimal capacidadKg) {
                getOrCreateEspecificaciones().setCapacidadKg(capacidadKg);
        }

        @Transient
        public Integer getCapacidadPasajeros() {
                return especificaciones != null ? especificaciones.getCapacidadPasajeros() : null;
        }

        public void setCapacidadPasajeros(Integer capacidadPasajeros) {
                getOrCreateEspecificaciones().setCapacidadPasajeros(capacidadPasajeros);
        }

        @Transient
        public String getImagenesVehiculo() {
                return adjuntos != null ? adjuntos.getImagenesVehiculo() : null;
        }

        public void setImagenesVehiculo(String imagenesVehiculo) {
                getOrCreateAdjuntos().setImagenesVehiculo(imagenesVehiculo);
        }

        @Transient
        public String getImagenesDocumentos() {
                return adjuntos != null ? adjuntos.getImagenesDocumentos() : null;
        }

        public void setImagenesDocumentos(String imagenesDocumentos) {
                getOrCreateAdjuntos().setImagenesDocumentos(imagenesDocumentos);
        }

        @Transient
        public TipoCombustible getTipoCombustible() {
                return especificaciones != null ? especificaciones.getTipoCombustible() : null;
        }

        public void setTipoCombustible(TipoCombustible tipoCombustible) {
                getOrCreateEspecificaciones().setTipoCombustible(tipoCombustible);
        }

        @Transient
        public String getChasis() {
                return especificaciones != null ? especificaciones.getChasis() : null;
        }

        public void setChasis(String chasis) {
                getOrCreateEspecificaciones().setChasis(chasis);
        }

        @Transient
        public Boolean getAireAcondicionado() {
                return especificaciones != null ? especificaciones.getAireAcondicionado() : null;
        }

        public void setAireAcondicionado(Boolean aireAcondicionado) {
                getOrCreateEspecificaciones().setAireAcondicionado(aireAcondicionado);
        }

        @Transient
        public Integer getMantenimientoMotorIntervalo() {
                return especificaciones != null ? especificaciones.getMantenimientoMotorIntervalo() : null;
        }

        public void setMantenimientoMotorIntervalo(Integer mantenimientoMotorIntervalo) {
                getOrCreateEspecificaciones().setMantenimientoMotorIntervalo(mantenimientoMotorIntervalo);
        }

        @Transient
        public Integer getMantenimientoCajaIntervalo() {
                return especificaciones != null ? especificaciones.getMantenimientoCajaIntervalo() : null;
        }

        public void setMantenimientoCajaIntervalo(Integer mantenimientoCajaIntervalo) {
                getOrCreateEspecificaciones().setMantenimientoCajaIntervalo(mantenimientoCajaIntervalo);
        }

        @Transient
        public String getSituacionPago() {
                return finanzas != null ? finanzas.getSituacionPago() : null;
        }

        public void setSituacionPago(String situacionPago) {
                getOrCreateFinanzas().setSituacionPago(situacionPago);
        }

        @Transient
        public Proveedor getProveedor() {
                return finanzas != null ? finanzas.getProveedor() : null;
        }

        public void setProveedor(Proveedor proveedor) {
                getOrCreateFinanzas().setProveedor(proveedor);
        }

        @Transient
        public Moneda getMoneda() {
                return finanzas != null ? finanzas.getMoneda() : null;
        }

        public void setMoneda(Moneda moneda) {
                getOrCreateFinanzas().setMoneda(moneda);
        }

        @Transient
        public BigDecimal getMontoTotal() {
                return finanzas != null ? finanzas.getMontoTotal() : null;
        }

        public void setMontoTotal(BigDecimal montoTotal) {
                getOrCreateFinanzas().setMontoTotal(montoTotal);
        }

        @Transient
        public BigDecimal getMontoYaPagado() {
                return finanzas != null ? finanzas.getMontoYaPagado() : null;
        }

        public void setMontoYaPagado(BigDecimal montoYaPagado) {
                getOrCreateFinanzas().setMontoYaPagado(montoYaPagado);
        }

        @Transient
        public Integer getCantidadCuotas() {
                return finanzas != null ? finanzas.getCantidadCuotas() : null;
        }

        public void setCantidadCuotas(Integer cantidadCuotas) {
                getOrCreateFinanzas().setCantidadCuotas(cantidadCuotas);
        }

        @Transient
        public Integer getCantidadCuotasPagadas() {
                return finanzas != null ? finanzas.getCantidadCuotasPagadas() : null;
        }

        public void setCantidadCuotasPagadas(Integer cantidadCuotasPagadas) {
                getOrCreateFinanzas().setCantidadCuotasPagadas(cantidadCuotasPagadas);
        }

        @Transient
        public Integer getDiaVencimiento() {
                return finanzas != null ? finanzas.getDiaVencimiento() : null;
        }

        public void setDiaVencimiento(Integer diaVencimiento) {
                getOrCreateFinanzas().setDiaVencimiento(diaVencimiento);
        }

        private VehiculoEspecificaciones getOrCreateEspecificaciones() {
                if (this.especificaciones == null) {
                        VehiculoEspecificaciones created = new VehiculoEspecificaciones();
                        created.setVehiculo(this);
                        this.especificaciones = created;
                }
                return this.especificaciones;
        }

        private VehiculoAdjuntos getOrCreateAdjuntos() {
                if (this.adjuntos == null) {
                        VehiculoAdjuntos created = new VehiculoAdjuntos();
                        created.setVehiculo(this);
                        this.adjuntos = created;
                }
                return this.adjuntos;
        }

        private VehiculoFinanzas getOrCreateFinanzas() {
                if (this.finanzas == null) {
                        VehiculoFinanzas created = new VehiculoFinanzas();
                        created.setVehiculo(this);
                        this.finanzas = created;
                }
                return this.finanzas;
        }
}
