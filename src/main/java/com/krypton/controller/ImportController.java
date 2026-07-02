package com.krypton.controller;

import com.krypton.dto.response.ImportStatusResponse;
import com.krypton.service.ImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/xml")
    public ResponseEntity<ImportStatusResponse> importXml(@RequestParam("file") MultipartFile file) {
        ImportStatusResponse status = importService.startImportJob(file);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<ImportStatusResponse> getImportStatus(@PathVariable String jobId) {
        ImportStatusResponse status = importService.getJobStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}