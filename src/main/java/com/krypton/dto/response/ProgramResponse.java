package com.krypton.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramResponse {
    private String id;
    private String name;
    private Integer totalDays;
    private Boolean isActive;
    private Integer currentDay;
    private List<Map<String, Object>> days;
}