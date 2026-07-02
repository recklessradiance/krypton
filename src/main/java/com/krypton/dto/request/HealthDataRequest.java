package com.krypton.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthDataRequest {
    private Double sleepHours;
    private Double restingHeartRate;
    private Double bloodGlucose;
    private Double weight;
}\n