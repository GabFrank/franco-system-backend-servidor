package com.franco.dev.graphql.replication.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogicalReplicationInput {
    private String sucursalId;
    private List<String> tables;
} 