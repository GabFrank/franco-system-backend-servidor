//package com.franco.dev.security;
//
//import org.apache.catalina.connector.Connector;
//import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
//import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class MultipleConnectorsConfig {
//
//    @Bean
//    public ServletWebServerFactory servletContainer() {
//        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
//        tomcat.addAdditionalTomcatConnectors(createSslConnector());
//        return tomcat;
//    }
//
//    private Connector createSslConnector() {
//        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
//        connector.setPort(8081); // Set the HTTPS port here
//        // Specify SSL configuration for this connector
//        // Refer to your SSL setup
//        return connector;
//    }
//}
