package com.krypton.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportStatusResponse {
    private String jobId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private Integer progressPercent;
    private String message;
}