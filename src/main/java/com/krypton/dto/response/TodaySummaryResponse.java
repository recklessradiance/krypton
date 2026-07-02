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
public class TodaySummaryResponse {
    private String date;
    private Integer age;
    private Map<String, Boolean> checklist;
    private Map<String, Object> health;
    private Map<String, Object> workout;
}