package com.franco.dev.graphql.replication.types;

import com.franco.dev.domain.empresarial.Sucursal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogicalReplication {
    private String name;
    private Boolean enabled;
    private Sucursal sucursal;
    private List<String> tables;
} 