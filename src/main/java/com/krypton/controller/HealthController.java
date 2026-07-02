package com.krypton.controller;

import com.krypton.dto.request.DailyCheckInRequest;
import com.krypton.dto.response.TodaySummaryResponse;
import com.krypton.dto.response.PredictionResponse;
import com.krypton.service.HealthService;
import com.krypton.service.PredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;
    private final PredictionService predictionService;

    public HealthController(HealthService healthService, PredictionService predictionService) {
        this.healthService = healthService;
        this.predictionService = predictionService;
    }

    @PostMapping("/daily")
    public ResponseEntity<Void> submitDaily(@Valid @RequestBody DailyCheckInRequest request) {
        try {
            healthService.submitDaily(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit daily metrics", e);
        }
    }

    @GetMapping("/today")
    public ResponseEntity<TodaySummaryResponse> getTodaySummary() {
        try {
            String todayStr = LocalDate.now().toString();
            TodaySummaryResponse summary = healthService.getTodaySummary(todayStr);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch today's summary", e);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<TodaySummaryResponse>> getHistory(
            @RequestParam String from,
            @RequestParam String to) {
        try {
            List<TodaySummaryResponse> history = healthService.getHistory(from, to);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch health history", e);
        }
    }

    @GetMapping("/predictions")
    public ResponseEntity<PredictionResponse> getPredictions(
            @RequestParam List<String> metrics,
            @RequestParam List<String> horizons) {
        try {
            PredictionResponse response = predictionService.getPredictions(metrics, horizons);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate predictions", e);
        }
    }
}