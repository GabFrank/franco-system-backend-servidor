package com.franco.dev.config.multitenant;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

public class TenantPropertiesLoader {

    public static Properties loadTenantProperties(String tenantId) throws IOException {
        String propertiesFileName = "hibernate-" + tenantId + ".properties";
        Resource resource = new ClassPathResource(propertiesFileName);
        return PropertiesLoaderUtils.loadProperties(resource);
    }
}
