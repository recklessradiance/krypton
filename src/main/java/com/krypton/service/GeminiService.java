package com.krypton.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.krypton.exception.ClinicalExtractionException;
import com.krypton.model.ExtractedMetric;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Service
public class GeminiService {

    private final ObjectMapper objectMapper;

    @Value("${krypton.gemini-api-key:}")
    private String geminiApiKey;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, ExtractedMetric> extractMetrics(String reportText) {
        String projectId = System.getenv("FIREBASE_PROJECT_ID");
        if (projectId == null || projectId.isEmpty()) {
            projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        }
        if (projectId == null || projectId.isEmpty()) {
            projectId = System.getenv("GCP_PROJECT_ID");
        }

        String location = System.getenv("VERTEX_AI_LOCATION");
        if (location == null || location.isEmpty()) {
            location = "us-central1";
        }

        GoogleCredentials credentials = null;
        try {
            credentials = getCredentials();
            if (projectId == null || projectId.isEmpty()) {
                if (credentials instanceof com.google.auth.oauth2.ServiceAccountCredentials) {
                    projectId = ((com.google.auth.oauth2.ServiceAccountCredentials) credentials).getProjectId();
                }
            }
        } catch (IOException e) {
            // Ignore, SDK will attempt default options
        }

        if (projectId == null || projectId.isEmpty()) {
            projectId = "default-project";
        }

        try {
            VertexAI.Builder builder = new VertexAI.Builder()
                    .setProjectId(projectId)
                    .setLocation(location);
            if (credentials != null) {
                builder.setCredentials(credentials);
            }

            try (VertexAI vertexAi = builder.build()) {
                GenerationConfig generationConfig = GenerationConfig.newBuilder()
                        .setResponseMimeType("application/json")
                        .build();

                GenerativeModel model = new GenerativeModel("gemini-1.5-flash", vertexAi).withGenerationConfig(generationConfig);

                String prompt = "You are a clinical report extraction assistant. Your job is to extract medical metrics from the clinical text provided. " +
                        "Extract the name of the test/metric (in camelCase, e.g. totalCholesterol, ldlCholesterol, hba1c, thyroidStimulatingHarmone, vitaminD) and map it to a structured JSON object. " +
                        "Each metric object must have exactly the following fields:\n" +
                        "- value: Double (e.g. 185.0 or 5.4, numeric value only, null if missing or invalid)\n" +
                        "- unit: String (e.g. \"mg/dL\", \"%\", null if missing)\n" +
                        "- refRange: String (the reference range exactly as written, e.g. \"< 200\" or \"70 - 100\", null if missing)\n" +
                        "- status: String (strictly one of: \"normal\", \"low\", \"high\")\n\n" +
                        "Determine the status by comparing value with the reference range (low if below, high if above, normal if within). " +
                        "If status cannot be determined, set it to \"normal\".\n\n" +
                        "Return a single JSON object where the keys are the metric names (e.g., \"totalCholesterol\") and the values are their extracted metrics. " +
                        "Only output raw JSON. Do not include markdown wraps or anything else.\n\n" +
                        "Clinical report text:\n" +
                        reportText;

                GenerateContentResponse response = model.generateContent(prompt);
                String jsonResponse = ResponseHandler.getText(response);

                if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                    throw new ClinicalExtractionException("Gemini returned an empty response.");
                }

                // If output somehow includes markdown formatting, strip it
                if (jsonResponse.startsWith("```")) {
                    jsonResponse = jsonResponse.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
                }

                return objectMapper.readValue(jsonResponse, new TypeReference<Map<String, ExtractedMetric>>() {});
            }
        } catch (Exception e) {
            throw new ClinicalExtractionException("Failed to extract clinical metrics using Gemini: " + e.getMessage());
        }
    }

    private GoogleCredentials getCredentials() throws IOException {
        String firebaseConfigEnv = System.getenv("FIREBASE_CONFIG");
        if (firebaseConfigEnv != null && !firebaseConfigEnv.isEmpty()) {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(firebaseConfigEnv.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
        }

        ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
        if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
                return GoogleCredentials.fromStream(is)
                        .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
            }
        }

        return GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
    }
}