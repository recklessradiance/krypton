package com.krypton.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalResponse {
    private String id;
    private String date;
    private String reportType;
    private String pdfDownloadUrl;
    private Map<String, Object> extractedMetrics;
}