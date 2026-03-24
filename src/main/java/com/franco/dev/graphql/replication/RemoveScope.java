package com.franco.dev.graphql.replication;

/**
 * Where to remove: central server only, filial only, or both.
 * Used by removeReplicationAdvanced mutation.
 */
public enum RemoveScope {
    CENTRAL,
    FILIAL,
    BOTH
}
