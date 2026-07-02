package com.krypton.controller;

import com.krypton.dto.request.ProgramUploadRequest;
import com.krypton.dto.response.ProgramResponse;
import com.krypton.service.ProgramService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping
    public ResponseEntity<List<ProgramResponse>> listPrograms() {
        try {
            return ResponseEntity.ok(programService.listPrograms());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list programs", e);
        }
    }

    @PostMapping
    public ResponseEntity<ProgramResponse> uploadProgram(@Valid @RequestBody ProgramUploadRequest request) {
        try {
            return ResponseEntity.ok(programService.uploadProgram(request));
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload program", e);
        }
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activateProgram(@PathVariable String id) {
        try {
            programService.activateProgram(id);
            return ResponseEntity.ok().build();
        } catch (com.krypton.exception.ProgramNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to activate program", e);
        }
    }

    @GetMapping("/today")
    public ResponseEntity<ProgramResponse> getTodayWorkout() {
        try {
            return ResponseEntity.ok(programService.getTodayWorkout());
        } catch (com.krypton.exception.ProgramNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch today's workout", e);
        }
    }

    @PostMapping("/complete-day")
    public ResponseEntity<Void> completeDay() {
        try {
            programService.completeDay();
            return ResponseEntity.ok().build();
        } catch (com.krypton.exception.ProgramNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to complete workout day", e);
        }
    }
}