package com.franco.dev.domain.activos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vehiculo_adjuntos", schema = "vehiculos")
public class VehiculoAdjuntos {

    @Id
    @Column(name = "vehiculo_id")
    private Long vehiculoId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(name = "imagenes_vehiculo")
    private String imagenesVehiculo;

    @Column(name = "imagenes_documentos")
    private String imagenesDocumentos;
}
