package com.khetisetu.event.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.project-id:}")
    private String projectId;

    @Value("${firebase.client-email:}")
    private String clientEmail;

    @Value("${firebase.private-key:}")
    private String privateKey;

    @Value("${firebase.client-id:}")
    private String clientId;

    @Value("${firebase.private-key-id:}")
    private String privateKeyId;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            logger.info("Firebase Application already initialized");
            return FirebaseApp.getInstance();
        }

        try {
            GoogleCredentials credentials;

            if (StringUtils.hasText(privateKey)) {
                logger.info("Loading Firebase credentials from properties (Project ID: {})", projectId);
                String formattedKey = privateKey.replace("\\n", "\n");
                credentials = GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(
                        createGoogleCredentialsJson(projectId, clientEmail, formattedKey, clientId, privateKeyId)
                                .getBytes()));
            } else {
                logger.warn("No Firebase private key found in environment variables. Push notifications may fail.");
                return null;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            return FirebaseApp.initializeApp(options);

        } catch (IOException e) {
            logger.error("Failed to initialize Firebase: {}", e.getMessage());
            return null;
        }
    }

    private String createGoogleCredentialsJson(String projectId, String clientEmail, String privateKey,
            String clientId, String privateKeyId) {
        return String.format(
                "{\n" +
                        "  \"type\": \"service_account\",\n" +
                        "  \"project_id\": \"%s\",\n" +
                        "  \"private_key_id\": \"%s\",\n" +
                        "  \"private_key\": \"%s\",\n" +
                        "  \"client_id\": \"%s\",\n" +
                        "  \"client_email\": \"%s\",\n" +
                        "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
                        "  \"token_uri\": \"https://oauth2.googleapis.com/token\",\n" +
                        "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
                        "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%%40%s.iam.gserviceaccount.com\"\n"
                        + "}",
                projectId, privateKeyId, privateKey.replace("\n", "\\n"), clientId, clientEmail, projectId);
    }
}
