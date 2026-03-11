package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "recepcion_mercaderia_nota", schema = "operaciones")
public class RecepcionMercaderiaNota implements Identifiable<Long> {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recepcion_mercaderia_id", nullable = false)
    private RecepcionMercaderia recepcionMercaderia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_recepcion_id", nullable = false)
    private NotaRecepcion notaRecepcion;
} 