package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetrics {
    private String date;
    private Checklist checklist;
    private WorkoutLog workout;
    private Health health;
    private String notes;
    private String source;
    private Instant createdAt;
    private Instant updatedAt;
}