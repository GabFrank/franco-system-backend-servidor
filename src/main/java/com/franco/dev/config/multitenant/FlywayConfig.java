package com.franco.dev.config.multitenant;

import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class FlywayConfig {

//    @Autowired
//    private TenantContext tenantContext;

    @PostConstruct
    public void migrateFlyway() {
//        Set<String> tenantIds = tenantContext.getAllTenantKeys();
//        try {
//            Properties tenantProperties = TenantPropertiesLoader.loadTenantProperties("default");
//
//            String url = tenantProperties.getProperty("hibernate.connection.url");
//            String username = tenantProperties.getProperty("hibernate.connection.username");
//            String password = tenantProperties.getProperty("hibernate.connection.password");
//            String locations = tenantProperties.getProperty("spring.flyway.locations", "classpath:db/migration");
//
//            Flyway flyway = Flyway.configure()
//                    .dataSource(url, username, password)
//                    .locations(locations)
//                    .baselineOnMigrate(true)
//                    .load();
//            flyway.migrate();
//
//        } catch (IOException e) {
//            // Handle exception (e.g., log it)
//            e.printStackTrace();
//        }
//        for (String tenantId : tenantIds) {
//            try {
//                Properties tenantProperties = TenantPropertiesLoader.loadTenantProperties(tenantId);
//
//                String url = tenantProperties.getProperty("hibernate.connection.url");
//                String username = tenantProperties.getProperty("hibernate.connection.username");
//                String password = tenantProperties.getProperty("hibernate.connection.password");
//                String locations = tenantProperties.getProperty("spring.flyway.locations");
//                String table = tenantProperties.getProperty("flyway.table");
//
//                Flyway flyway = Flyway.configure()
//                        .dataSource(url, username, password)
//                        .locations(locations)
//                        .baselineOnMigrate(true)
//                        .table(table)
//                        .load();
//                flyway.migrate();
//
//            } catch (IOException e) {
//                // Handle exception (e.g., log it)
//                e.printStackTrace();
//            }
//        }
    }
}

