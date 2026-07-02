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
public class Exercise {
    private String name;
    private Integer sets;
    private List<Integer> reps;
    private Boolean perArm;
    private Boolean perLeg;
    private Boolean perSide;
    private String duration;
    private String unit;
    private String note;
}\n