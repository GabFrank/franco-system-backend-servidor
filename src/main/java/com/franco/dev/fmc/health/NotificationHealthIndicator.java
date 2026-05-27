package com.franco.dev.fmc.health;

import com.google.firebase.FirebaseApp;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.firebase-enabled", havingValue = "true", matchIfMissing = false)
public class NotificationHealthIndicator implements HealthIndicator {

    private final ResourceLoader resourceLoader;

    @Value("${app.firebase-configuration-file}")
    private String firebaseConfigFile;

    public NotificationHealthIndicator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Health health() {
        try {
            Resource resource = resolveFirebaseConfigResource();
            if (!resource.exists()) {
                return Health.down().withDetail("firebaseConfig", "Archivo no encontrado").build();
            }
            if (FirebaseApp.getApps().isEmpty()) {
                return Health.down().withDetail("firebase", "Aplicación no inicializada").build();
            }
            resource.getInputStream().close();
            return Health.up().build();
        } catch (IOException e) {
            return Health.down(e).build();
        }
    }

    private Resource resolveFirebaseConfigResource() {
        if (firebaseConfigFile == null || firebaseConfigFile.trim().isEmpty()) {
            return resourceLoader.getResource("file:/dev/null");
        }
        if (firebaseConfigFile.startsWith("file:")) {
            return resourceLoader.getResource(firebaseConfigFile);
        }
        return resourceLoader.getResource("file:" + firebaseConfigFile);
    }
}
