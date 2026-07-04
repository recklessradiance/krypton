package com.krypton.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import com.google.cloud.storage.Bucket;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        InputStream serviceAccountStream = null;
        String firebaseConfigEnv = System.getenv("FIREBASE_CONFIG");

        if (firebaseConfigEnv != null && !firebaseConfigEnv.isEmpty()) {
            serviceAccountStream = new ByteArrayInputStream(firebaseConfigEnv.getBytes(StandardCharsets.UTF_8));
        } else {
            Resource resource = new ClassPathResource("firebase-service-account.json");
            if (resource.exists()) {
                serviceAccountStream = resource.getInputStream();
            }
        }

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();
        if (serviceAccountStream != null) {
            optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccountStream));
        } else {
            optionsBuilder.setCredentials(GoogleCredentials.getApplicationDefault());
        }

        // Set storage bucket name if provided (typically project-id.appspot.com)
        String projectId = System.getenv("FIREBASE_PROJECT_ID");
        if (projectId != null && !projectId.isEmpty()) {
            optionsBuilder.setStorageBucket(projectId + ".appspot.com");
        }

        FirebaseApp.initializeApp(optionsBuilder.build());
    }

    @Bean
    public Firestore firestore() {
        return FirestoreClient.getFirestore();
    }

    @Bean
    public Bucket storageBucket() {
        try {
            return StorageClient.getInstance().bucket();
        } catch (Exception e) {
            System.err.println("Firebase Storage Bucket could not be initialized: " + e.getMessage());
            return null;
        }
    }
}