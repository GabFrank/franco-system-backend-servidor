package com.franco.dev.domain.configuraciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "replication_direction",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "replication_table", schema = "configuraciones")
public class ReplicationTable implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Full table name with schema (e.g., "personas.usuario")
    @Column(nullable = false, unique = true)
    private String tableName;
    
    // Direction of replication
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    @Type(type = "replication_direction")
    private ReplicationDirection direction;
    
    // Whether this table should be replicated
    @Column(nullable = false)
    private Boolean enabled = true;
    
    // Additional notes or description
    @Column(length = 500)
    private String description;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;
    
    // Enum for replication direction
    public enum ReplicationDirection {
        MAIN_TO_ALL,         // Replicate from central server to all branches
        MAIN_TO_SPECIFIC,    // Replicate from central server to specific branches
        BRANCH_TO_MAIN       // Replicate from branch to central server
    }
} 