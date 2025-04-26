//package com.franco.dev.config.multitenant;
//
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.PlatformTransactionManager;
//import org.springframework.transaction.TransactionDefinition;
//import org.springframework.transaction.TransactionStatus;
//import org.springframework.transaction.support.DefaultTransactionDefinition;
//
//@Aspect
//@Component
//public class MultiTenantTransactionalAspect {
//
//    @Autowired
//    @Qualifier("transactionManager")  // Ensure this is the correct JpaTransactionManager
//    private PlatformTransactionManager transactionManager;
//
//    @Around("@annotation(MultiTenantTransactional)")
//    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
//        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
//        def.setName("multiTenantTx");
//        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
//
//        TransactionStatus status = transactionManager.getTransaction(def);
//
//        try {
//            Object result = joinPoint.proceed();
//            transactionManager.commit(status);
//            return result;
//        } catch (Throwable ex) {
//            transactionManager.rollback(status);
//            throw ex;
//        }
//    }
//}
//
