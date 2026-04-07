package com.franco.dev.domain.configuraciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import com.vladmihalcea.hibernate.type.array.LongArrayType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDefs({
        @TypeDef(name = "replication_direction", typeClass = PostgreSQLEnumType.class),
        @TypeDef(name = "long-array", typeClass = LongArrayType.class)
})
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

    /** For MAIN_TO_SPECIFIC: destination branch IDs. Null or empty for other directions. */
    @Type(type = "long-array")
    @Column(name = "branch_ids", columnDefinition = "bigint[]")
    private Long[] branchIds;

    /** When direction=BRANCH_TO_MAIN and true: table is also in central_filialX_pub (central to branch with WHERE sucursal_id = X) for all branches. */
    @Column(name = "replicate_central_to_branch_with_filter", nullable = false)
    private Boolean replicateCentralToBranchWithFilter = false;

    public List<Long> getBranchIdsList() {
        if (branchIds == null || branchIds.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(branchIds).collect(Collectors.toList());
    }

    public void setBranchIdsList(List<Long> ids) {
        this.branchIds = (ids == null || ids.isEmpty()) ? null : ids.toArray(new Long[0]);
    }

    // Enum for replication direction
    public enum ReplicationDirection {
        MAIN_TO_ALL,         // Replicate from central server to all branches
        MAIN_TO_SPECIFIC,    // Replicate from central server to specific branches
        BRANCH_TO_MAIN       // Replicate from branch to central server
    }
} 