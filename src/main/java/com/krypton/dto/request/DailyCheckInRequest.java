package com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCheckInRequest {
    @NotNull(message = "Date is required")
    private String date;
    
    private Map<String, Boolean> checklist;
    
    private Map<String, Object> health;
    
    private String notes;
}\n