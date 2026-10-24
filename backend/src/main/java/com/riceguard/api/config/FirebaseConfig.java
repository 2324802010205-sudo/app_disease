package com.riceguard.api.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Configuration
public class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    FirebaseApp firebaseApp(AppProperties props) throws IOException {
        AppProperties.Firebase fb = props.firebase();
        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials(fb));
        if (!fb.projectId().isBlank()) options.setProjectId(fb.projectId());
        log.info("Khởi tạo Firebase (project={}, emulator={})", fb.projectId().isBlank() ? "<từ credentials>" : fb.projectId(), fb.emulator());
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }

    private static GoogleCredentials credentials(AppProperties.Firebase fb) throws IOException {
        if (fb.emulator()) {
            // Emulator không kiểm tra credentials, chỉ cần một token giả.
            return GoogleCredentials.create(new AccessToken("owner", new Date(Long.MAX_VALUE / 4)));
        }
        String raw = fb.credentials() == null ? "" : fb.credentials().trim();
        if (!raw.isEmpty()) {
            String json = raw.startsWith("{") ? raw : new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (fb.credentialsFile() != null && !fb.credentialsFile().isBlank()) {
            try (InputStream in = new FileInputStream(fb.credentialsFile())) {
                return GoogleCredentials.fromStream(in);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
