package com.krypton.controller;

import com.krypton.dto.response.ClinicalResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/clinical")
public class ClinicalController {

    @PostMapping("/upload")
    public ResponseEntity<ClinicalResponse> uploadPdf(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ClinicalResponse.builder().build());
    }

    @GetMapping("/latest")
    public ResponseEntity<List<ClinicalResponse>> getLatestClinical() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicalResponse> getClinicalRecord(@PathVariable String id) {
        return ResponseEntity.ok(ClinicalResponse.builder().build());
    }
}\n