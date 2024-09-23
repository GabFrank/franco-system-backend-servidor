package com.franco.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import java.io.IOException;
import java.util.Collections;

@EnableRetry
@SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceTransactionManagerAutoConfiguration.class})
public class FrancoSystemsApplication {

    @Autowired
    private ObjectMapper objectMapper;

    public static void main(String[] args) throws IOException {

        System.out.println("Iniciando sistema");
        SpringApplication.run(FrancoSystemsApplication.class, args);

    }

    @Bean
    public RestTemplate getResTemplate() {
        return new RestTemplate();
    }

    /**
     * Register the {@link OpenEntityManagerInViewFilter} so that the
     * GraphQL-Servlet can handle lazy loads during execution.
     *
     * @return
     */
    @Bean
    public Filter OpenFilter() {
        return new OpenEntityManagerInViewFilter();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> simpleCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Collections.singletonList("*"));
        config.setAllowedMethods(Collections.singletonList("*"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @PostConstruct
    public void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Bean
    public void getHomePath() {
        System.out.println("Iniciando test database change");
    }

    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }

}
