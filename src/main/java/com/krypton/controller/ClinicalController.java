package com.krypton.controller;

import com.krypton.dto.response.ClinicalResponse;
import com.krypton.service.ClinicalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/clinical")
public class ClinicalController {

    private final ClinicalService clinicalService;

    public ClinicalController(ClinicalService clinicalService) {
        this.clinicalService = clinicalService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ClinicalResponse> uploadPdf(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(clinicalService.upload(file));
    }

    @GetMapping("/latest")
    public ResponseEntity<List<ClinicalResponse>> getLatestClinical() {
        return ResponseEntity.ok(clinicalService.getLatestRecords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicalResponse> getClinicalRecord(@PathVariable String id) {
        return ResponseEntity.ok(clinicalService.getRecord(id));
    }
}