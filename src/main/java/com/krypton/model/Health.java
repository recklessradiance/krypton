package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Health {
    private Double sleepHours;
    private Double restingHeartRate;
    private Double bloodGlucoseFasting;
    private Double bloodGlucosePP;
    private Double weight;
}\n