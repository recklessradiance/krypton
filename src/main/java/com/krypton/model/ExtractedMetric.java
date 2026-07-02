package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedMetric {
    private Double value;
    private String unit;
    private String refRange;
    private String status; // normal, low, high
}