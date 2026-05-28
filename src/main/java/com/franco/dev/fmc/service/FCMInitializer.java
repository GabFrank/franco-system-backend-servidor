package com.franco.dev.fmc.service;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Service
public class FCMInitializer {

    @Value("${app.firebase-configuration-file}")
    private String firebaseConfigPath;
    @Value("${app.firebase-enabled:true}")
    private boolean firebaseEnabled;
    private final ResourceLoader resourceLoader;
    Logger logger = LoggerFactory.getLogger(FCMInitializer.class);

    public FCMInitializer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initialize() {
        if (!firebaseEnabled) {
            logger.info("Firebase initialization skipped because app.firebase-enabled=false");
            return;
        }
        try {
            logger.info("Initializing Firebase using config path: {}", firebaseConfigPath);
            Resource configResource = resolveFirebaseConfigResource();
            if (!configResource.exists()) {
                logger.error("Error initializing Firebase: config file not found at {}", firebaseConfigPath);
                return;
            }
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(configResource.getInputStream()))
                    .setProjectId("bodega-franco-frc")
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase application has been initialized with project: bodega-franco-frc");
                logger.info("Firebase Sender ID: 170136643206");
            } else {
                logger.info("Firebase already initialized. Apps loaded: {}", FirebaseApp.getApps().size());
            }
        } catch (Exception e) {
            logger.error("Error initializing Firebase: " + e.getMessage());
        }
    }

    private Resource resolveFirebaseConfigResource() {
        if (firebaseConfigPath == null || firebaseConfigPath.trim().isEmpty()) {
            return resourceLoader.getResource("file:/dev/null");
        }
        if (firebaseConfigPath.startsWith("file:")) {
            return resourceLoader.getResource(firebaseConfigPath);
        }
        return resourceLoader.getResource("file:" + firebaseConfigPath);
    }
}
