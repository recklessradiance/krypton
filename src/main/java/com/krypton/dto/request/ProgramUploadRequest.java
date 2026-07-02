package com.krypton.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class ProgramUploadRequest {
    @NotBlank(message = "Program name is required")
    private String name;
    
    private List<Map<String, Object>> days;
}\n