package com.krypton.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.krypton.dto.response.ClinicalResponse;
import com.krypton.exception.ClinicalExtractionException;
import com.krypton.model.ClinicalRecord;
import com.krypton.model.ExtractedMetric;
import com.krypton.repository.ClinicalRecordRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ClinicalService {

    private final ClinicalRecordRepository clinicalRecordRepository;
    private final GeminiService geminiService;
    private final Bucket bucket;

    public ClinicalService(ClinicalRecordRepository clinicalRecordRepository,
                           GeminiService geminiService,
                           Optional<Bucket> bucket) {
        this.clinicalRecordRepository = clinicalRecordRepository;
        this.geminiService = geminiService;
        this.bucket = bucket.orElse(null);
    }

    public ClinicalResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ClinicalExtractionException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "clinical_" + System.currentTimeMillis() + ".pdf";
        }

        // Clean original filename to replace spaces
        String filename = originalFilename.replaceAll("\\s+", "_");
        String storagePath = "clinical/" + filename;

        // Determine date and reportType
        String date = LocalDate.now().toString();
        String reportType = "blood_work";

        String nameWithoutExtension = filename.contains(".") ?
                filename.substring(0, filename.lastIndexOf('.')) : filename;

        if (nameWithoutExtension.matches("^\\d{4}-\\d{2}-\\d{2}_.*")) {
            date = nameWithoutExtension.substring(0, 10);
            reportType = nameWithoutExtension.substring(11);
        } else if (nameWithoutExtension.matches("^\\d{4}-\\d{2}-\\d{2}")) {
            date = nameWithoutExtension;
        } else {
            reportType = nameWithoutExtension;
        }

        // 1. Upload to Firebase Storage
        if (bucket == null) {
            throw new ClinicalExtractionException("Firebase Storage bucket is not configured. Please activate Storage in the Firebase Console.");
        }
        try {
            bucket.create(storagePath, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new ClinicalExtractionException("Failed to save PDF to storage: " + e.getMessage());
        }

        // 2. Extract Text using PDFBox
        String extractedText;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        } catch (IOException e) {
            throw new ClinicalExtractionException("Failed to extract text from PDF: " + e.getMessage());
        }

        // 3. Extract Metrics using GeminiService
        Map<String, ExtractedMetric> extractedMetrics = geminiService.extractMetrics(extractedText);

        // 4. Save to Firestore
        ClinicalRecord record = ClinicalRecord.builder()
                .date(date)
                .reportType(reportType)
                .pdfStoragePath(storagePath)
                .extractedMetrics(extractedMetrics)
                .createdAt(Instant.now())
                .build();

        try {
            clinicalRecordRepository.save(record);
        } catch (Exception e) {
            throw new ClinicalExtractionException("Failed to save clinical record to database: " + e.getMessage());
        }

        return mapToResponse(record);
    }

    public List<ClinicalResponse> getLatestRecords() {
        try {
            List<ClinicalRecord> records = clinicalRecordRepository.findAllOrderByDateDesc();
            return records.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve latest clinical records", e);
        }
    }

    public ClinicalResponse getRecord(String id) {
        try {
            Optional<ClinicalRecord> recordOpt = clinicalRecordRepository.findById(id);
            if (recordOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No clinical record exists with ID: " + id);
            }
            return mapToResponse(recordOpt.get());
        } catch (Exception e) {
            if (e instanceof ResponseStatusException) {
                throw (ResponseStatusException) e;
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve clinical record", e);
        }
    }

    private ClinicalResponse mapToResponse(ClinicalRecord record) {
        String pdfDownloadUrl = null;
        if (record.getPdfStoragePath() != null) {
            pdfDownloadUrl = getDownloadUrl(record.getPdfStoragePath());
        }

        Map<String, Object> metricsMap = null;
        if (record.getExtractedMetrics() != null) {
            metricsMap = new HashMap<>(record.getExtractedMetrics());
        }

        return ClinicalResponse.builder()
                .id(record.getId())
                .date(record.getDate())
                .reportType(record.getReportType())
                .pdfDownloadUrl(pdfDownloadUrl)
                .extractedMetrics(metricsMap)
                .build();
    }

    private String getDownloadUrl(String blobPath) {
        if (bucket == null) {
            return "https://storage.googleapis.com/placeholder-bucket/" + blobPath;
        }
        try {
            Storage storage = bucket.getStorage();
            BlobId blobId = BlobId.of(bucket.getName(), blobPath);
            URL signedUrl = storage.signUrl(
                    BlobInfo.newBuilder(blobId).build(),
                    7, TimeUnit.DAYS
            );
            return signedUrl.toString();
        } catch (Exception e) {
            try {
                String encodedPath = URLEncoder.encode(blobPath, StandardCharsets.UTF_8.toString());
                return "https://firebasestorage.googleapis.com/v0/b/" + bucket.getName() + "/o/" + encodedPath + "?alt=media";
            } catch (Exception ex) {
                return "https://storage.googleapis.com/" + bucket.getName() + "/" + blobPath;
            }
        }
    }
}