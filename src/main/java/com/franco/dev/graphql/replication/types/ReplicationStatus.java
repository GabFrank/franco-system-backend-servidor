package com.franco.dev.graphql.replication.types;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReplicationStatus {
    private Boolean success;
    private String message;
} 