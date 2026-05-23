package com.franco.dev.domain.operaciones;

import com.franco.dev.domain.personas.Persona;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "hoja_ruta_acompanante", schema = "operaciones")
public class Acompanhante {

    @EmbeddedId
    private AcompanhanteId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("hojaRutaId")
    @JoinColumn(name = "hoja_ruta_id")
    private HojaRuta hojaRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("personaId")
    @JoinColumn(name = "persona_id")
    private Persona persona;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    public static class AcompanhanteId implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long hojaRutaId;
        private Long personaId;
    }
}
