package com.franco.dev.config.multitenant;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Properties;

public class TenantAwareSequenceGenerator extends SequenceStyleGenerator {

    private String sequenceName;

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws HibernateException {
        super.configure(type, params, serviceRegistry);
        sequenceName = params.getProperty(SEQUENCE_PARAM);
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        String tenantId = TenantContext.getCurrentTenant();

        if (tenantId == null || tenantId.equals("default")) {
            // Generate ID using the sequence
            return super.generate(session, object);
        } else {
            // For other tenants, return the existing ID or handle ID setting differently
            try {
                // Use reflection to get the existing ID
                Field idField = object.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                Object idValue = idField.get(object);
                if (idValue == null) {
                    throw new HibernateException("ID must be manually assigned for non-default tenants");
                }
                return (Serializable) idValue;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new HibernateException("Failed to access ID field", e);
            }
        }
    }
}

