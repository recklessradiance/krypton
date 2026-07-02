package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Program {
    private String id;
    private String name;
    private Integer totalDays;
    private Boolean isActive;
    private Integer currentDay;
    private List<ProgramDay> days;
    private Instant createdAt;
}\n