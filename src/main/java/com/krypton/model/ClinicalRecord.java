package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalRecord {
    private String id;
    private String date;
    private String reportType;
    private String pdfStoragePath;
    private Map<String, ExtractedMetric> extractedMetrics;
    private Instant createdAt;
}\n