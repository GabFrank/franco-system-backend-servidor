//package com.franco.dev.config.multitenant;
//
//import org.hibernate.SessionEventListener;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//
//@Component
//public class SlowQuerySessionEventListener implements SessionEventListener {
//
//    private final Logger logger = LoggerFactory.getLogger(getClass());
//
//    @Override
//    public void transactionCompletion(SessionEvent event) {
//        if (event.getSession().getFactory().getStatistics().isStatisticsEnabled()) {
//            long executionTime = event.getSession().getFactory().getStatistics().getQueryExecutionMaxTime();
//            if (executionTime > 5000) { // 5 seconds
//                logger.warn("Slow query detected: execution time was {} ms", executionTime);
//                String[] slowQueries = event.getSession().getFactory().getStatistics().getQueries();
//                for (String slowQuery : slowQueries) {
//                    logger.warn("Potential slow query: {}", slowQuery);
//                }
//            }
//        }
//    }
//
//    // other methods can be overridden here if needed
//}
//
