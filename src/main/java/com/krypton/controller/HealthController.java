package com.krypton.controller;

import com.krypton.dto.request.DailyCheckInRequest;
import com.krypton.dto.response.TodaySummaryResponse;
import com.krypton.dto.response.PredictionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @PostMapping("/daily")
    public ResponseEntity<Void> submitDaily(@Valid @RequestBody DailyCheckInRequest request) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/today")
    public ResponseEntity<TodaySummaryResponse> getTodaySummary() {
        return ResponseEntity.ok(TodaySummaryResponse.builder().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<TodaySummaryResponse>> getHistory(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/predictions")
    public ResponseEntity<PredictionResponse> getPredictions(
            @RequestParam List<String> metrics,
            @RequestParam List<String> horizons) {
        return ResponseEntity.ok(PredictionResponse.builder().build());
    }
}\n