package com.franco.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PrecioPorSucursalService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.TipoPrecioService;
import com.franco.dev.service.utils.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.update4j.Archive;
import org.update4j.Configuration;
import org.update4j.FileMetadata;
import org.update4j.UpdateOptions;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

@EnableRetry
@SpringBootApplication
public class FrancoSystemsApplication {

    public final static String SFG_MESSAGE_QUEUE = "test-queue";
    @Autowired
    ProductoService productoService;
    @Autowired
    TipoPrecioService tipoPrecioService;
    @Autowired
    PrecioPorSucursalService precioPorSucursalService;
    @Autowired
    ImageService imageService;
    private Logger log = LoggerFactory.getLogger(FrancoSystemsApplication.class);
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CodigoService codigoService;
    @Autowired
    private Environment env;

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

//	@Bean
//	public void crearThumbs(){
//		imageService.crearThumbs();
//	}

}
