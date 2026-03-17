package com.franco.dev.graphql.replication;

/**
 * What to remove: subscription only, publication only, or both.
 * Used by removeReplicationAdvanced mutation.
 */
public enum RemoveTarget {
    SUBSCRIPTION,
    PUBLICATION,
    BOTH
}
