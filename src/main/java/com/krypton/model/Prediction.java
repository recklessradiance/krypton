package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prediction {
    private String metric;
    private Double currentAvg;
    private Map<String, Object> horizons;
    private String trendDescription;
}\n