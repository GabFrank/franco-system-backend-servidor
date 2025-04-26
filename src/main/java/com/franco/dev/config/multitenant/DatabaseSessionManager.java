package com.franco.dev.config.multitenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

//@Service
//public class DatabaseSessionManager {
//
//    @PersistenceUnit
//    private EntityManagerFactory entityManagerFactory;
//    private static final Logger logger = LoggerFactory.getLogger(DatabaseSessionManager.class);
//
//    private static final ThreadLocal<EntityManagerHolder> threadLocalEntityManager = new ThreadLocal<>();
//
//    public void bindSession() {
//        if (!TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
//            EntityManager entityManager = entityManagerFactory.createEntityManager();
//            EntityManagerHolder entityManagerHolder = new EntityManagerHolder(entityManager);
//            threadLocalEntityManager.set(entityManagerHolder);
//            TransactionSynchronizationManager.bindResource(entityManagerFactory, entityManagerHolder);
//            logger.info("Session bound for entity manager: " + entityManager);
//        }
//    }
//
//    public void unbindSession() {
//        if (TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
//            EntityManagerHolder entityManagerHolder = (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(entityManagerFactory);
//            if (entityManagerHolder != null) {
//                EntityManager entityManager = entityManagerHolder.getEntityManager();
//                EntityManagerFactoryUtils.closeEntityManager(entityManager);
//                threadLocalEntityManager.remove();
//                logger.info("Session unbound and closed for entity manager: " + entityManager);
//            }
//        }
//    }
//}

