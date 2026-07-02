package com.krypton.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Checklist {
    private Boolean workout;
    private Boolean eggs;
    private Boolean quinoa;
    private Boolean vegCurry;
    private Boolean soya;
    private Boolean water;
}\n