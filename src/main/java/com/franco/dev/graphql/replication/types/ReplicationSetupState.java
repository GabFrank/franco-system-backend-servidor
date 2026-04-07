package com.franco.dev.graphql.replication.types;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReplicationSetupState {
    private Boolean centralPublicationExists;
    private Boolean centralSubscriptionExists;
    private Boolean filialReachable;
    private Boolean filialPublicationExists;
    private Boolean filialSubscriptionBidiExists;
    private Boolean filialSubscriptionCentralExists;
}
