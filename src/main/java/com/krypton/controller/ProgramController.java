package com.krypton.controller;

import com.krypton.dto.request.ProgramUploadRequest;
import com.krypton.dto.response.ProgramResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    @GetMapping
    public ResponseEntity<List<ProgramResponse>> listPrograms() {
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    public ResponseEntity<ProgramResponse> uploadProgram(@Valid @RequestBody ProgramUploadRequest request) {
        return ResponseEntity.ok(ProgramResponse.builder().build());
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activateProgram(@PathVariable String id) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/today")
    public ResponseEntity<ProgramResponse> getTodayWorkout() {
        return ResponseEntity.ok(ProgramResponse.builder().build());
    }

    @PostMapping("/complete-day")
    public ResponseEntity<Void> completeDay() {
        return ResponseEntity.ok().build();
    }
}\n