package com.franco.dev.config.multitenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    @Autowired
    private TenantContext tenantContext;

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = tenantContext.getCurrentTenant();
        return tenant != null ? tenant : "default";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
