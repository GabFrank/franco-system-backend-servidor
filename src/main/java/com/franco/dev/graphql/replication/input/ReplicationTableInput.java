package com.franco.dev.graphql.replication.input;

import com.franco.dev.domain.configuraciones.ReplicationTable.ReplicationDirection;
import lombok.Data;

import java.util.List;

@Data
public class ReplicationTableInput {
    private Long id;
    private String tableName;
    private ReplicationDirection direction;
    private Boolean enabled;
    private String description;
    private Long usuarioId;
    private List<Long> branchIds;
    private Boolean replicateCentralToBranchWithFilter;
} 