package com.krypton.controller;

import com.krypton.dto.response.ImportStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    @PostMapping("/xml")
    public ResponseEntity<ImportStatusResponse> importXml(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ImportStatusResponse.builder().build());
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<ImportStatusResponse> getImportStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(ImportStatusResponse.builder().build());
    }
}\n