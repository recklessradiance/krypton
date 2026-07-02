package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutLog {
    private String programName;
    private Integer programDay;
    private String title;
    private List<Exercise> exercises;
}