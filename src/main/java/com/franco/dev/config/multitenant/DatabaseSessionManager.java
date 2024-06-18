package com.franco.dev.config.multitenant;

import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

@Service
public class DatabaseSessionManager {

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private static ThreadLocal<EntityManagerHolder> threadLocalEntityManager = new ThreadLocal<>();

    public void bindSession() {
        if (!TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            EntityManagerHolder entityManagerHolder = new EntityManagerHolder(entityManager);
            threadLocalEntityManager.set(entityManagerHolder);
            TransactionSynchronizationManager.bindResource(entityManagerFactory, entityManagerHolder);
        }
    }

    public void unbindSession() {
        if (TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
            EntityManagerHolder entityManagerHolder = (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            if (entityManagerHolder != null) {
                EntityManagerFactoryUtils.closeEntityManager(entityManagerHolder.getEntityManager());
                threadLocalEntityManager.remove();
            }
        }
    }
}
