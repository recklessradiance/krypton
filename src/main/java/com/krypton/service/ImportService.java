package com.krypton.service;

import com.krypton.dto.response.ImportStatusResponse;
import com.krypton.exception.ImportFailedException;
import com.krypton.model.DailyMetrics;
import com.krypton.model.Exercise;
import com.krypton.model.Health;
import com.krypton.model.WorkoutLog;
import com.krypton.repository.DailyMetricsRepository;
import com.krypton.util.XmlStreamParser;
import com.krypton.util.JsonStreamParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ImportService {

    private final XmlStreamParser xmlStreamParser;
    private final JsonStreamParser jsonStreamParser;
    private final DailyMetricsRepository repository;
    private final Map<String, ImportStatusResponse> jobStatuses = new ConcurrentHashMap<>();

    public ImportService(XmlStreamParser xmlStreamParser, JsonStreamParser jsonStreamParser, DailyMetricsRepository repository) {
        this.xmlStreamParser = xmlStreamParser;
        this.jsonStreamParser = jsonStreamParser;
        this.repository = repository;
    }

    public ImportStatusResponse startImportJob(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportFailedException("Uploaded file is empty");
        }

        String jobId = UUID.randomUUID().toString();
        
        Path tempFilePath;
        try {
            tempFilePath = Files.createTempFile("krypton-import-", ".xml");
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store uploaded file for job {}", jobId, e);
            throw new ImportFailedException("Failed to store uploaded file: " + e.getMessage());
        }

        ImportStatusResponse status = ImportStatusResponse.builder()
                .jobId(jobId)
                .status("PENDING")
                .progressPercent(0)
                .message("Job submitted, queued for processing...")
                .build();

        jobStatuses.put(jobId, status);

        // Run asynchronously
        CompletableFuture.runAsync(() -> runImportAsync(jobId, tempFilePath));

        return status;
    }

    public ImportStatusResponse getJobStatus(String jobId) {
        return jobStatuses.get(jobId);
    }

    public ImportStatusResponse startJsonImportJob(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportFailedException("Uploaded file is empty");
        }

        String jobId = UUID.randomUUID().toString();
        
        Path tempFilePath;
        try {
            tempFilePath = Files.createTempFile("krypton-import-", ".json");
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store uploaded file for job {}", jobId, e);
            throw new ImportFailedException("Failed to store uploaded file: " + e.getMessage());
        }

        ImportStatusResponse status = ImportStatusResponse.builder()
                .jobId(jobId)
                .status("PENDING")
                .progressPercent(0)
                .message("Job submitted, queued for processing...")
                .build();

        jobStatuses.put(jobId, status);

        // Run asynchronously
        CompletableFuture.runAsync(() -> runJsonImportAsync(jobId, tempFilePath));

        return status;
    }

    @Async
    public void runJsonImportAsync(String jobId, Path tempFilePath) {
        ImportStatusResponse status = jobStatuses.get(jobId);
        if (status != null) {
            status.setStatus("PROCESSING");
            status.setMessage("Starting JSON parsing...");
            status.setProgressPercent(0);
        }

        try {
            List<DailyMetrics> parsedMetrics = jsonStreamParser.parseJson(tempFilePath, progress -> {
                ImportStatusResponse s = jobStatuses.get(jobId);
                if (s != null) {
                    int scaledProgress = (int) (progress * 0.9);
                    s.setProgressPercent(scaledProgress);
                    s.setMessage("Parsing JSON: " + scaledProgress + "%");
                }
            });

            ImportStatusResponse s = jobStatuses.get(jobId);
            if (s != null) {
                s.setMessage("Saving metrics to database...");
            }

            int total = parsedMetrics.size();
            int saved = 0;

            for (DailyMetrics metrics : parsedMetrics) {
                saveOrMerge(metrics);
                saved++;
                
                s = jobStatuses.get(jobId);
                if (s != null) {
                    int saveProgress = 90 + (int) ((saved * 10.0) / total);
                    s.setProgressPercent(saveProgress);
                    s.setMessage("Saving metrics to database: " + saveProgress + "%");
                }
            }

            s = jobStatuses.get(jobId);
            if (s != null) {
                s.setStatus("COMPLETED");
                s.setProgressPercent(100);
                s.setMessage("Import completed successfully. Processed " + total + " days.");
            }

        } catch (Exception e) {
            log.error("Import job {} failed", jobId, e);
            ImportStatusResponse s = jobStatuses.get(jobId);
            if (s != null) {
                s.setStatus("FAILED");
                s.setMessage("Import failed: " + e.getMessage());
            }
        } finally {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException e) {
                log.error("Failed to delete temp file {}", tempFilePath, e);
            }
        }
    }

    @Async
    public void runImportAsync(String jobId, Path tempFilePath) {
        ImportStatusResponse status = jobStatuses.get(jobId);
        if (status != null) {
            status.setStatus("PROCESSING");
            status.setMessage("Starting XML parsing...");
            status.setProgressPercent(0);
        }

        try {
            List<DailyMetrics> parsedMetrics = xmlStreamParser.parseXml(tempFilePath, progress -> {
                ImportStatusResponse s = jobStatuses.get(jobId);
                if (s != null) {
                    int scaledProgress = (int) (progress * 0.9);
                    s.setProgressPercent(scaledProgress);
                    s.setMessage("Parsing XML: " + scaledProgress + "%");
                }
            });

            ImportStatusResponse s = jobStatuses.get(jobId);
            if (s != null) {
                s.setMessage("Saving metrics to database...");
            }

            int total = parsedMetrics.size();
            int saved = 0;

            for (DailyMetrics metrics : parsedMetrics) {
                saveOrMerge(metrics);
                saved++;
                
                s = jobStatuses.get(jobId);
                if (s != null) {
                    int saveProgress = 90 + (int) ((saved * 10.0) / total);
                    s.setProgressPercent(saveProgress);
                    s.setMessage("Saving metrics to database: " + saveProgress + "%");
                }
            }

            s = jobStatuses.get(jobId);
            if (s != null) {
                s.setStatus("COMPLETED");
                s.setProgressPercent(100);
                s.setMessage("Import completed successfully. Processed " + total + " days.");
            }

        } catch (Exception e) {
            log.error("Import job {} failed", jobId, e);
            ImportStatusResponse s = jobStatuses.get(jobId);
            if (s != null) {
                s.setStatus("FAILED");
                s.setMessage("Import failed: " + e.getMessage());
            }
        } finally {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException e) {
                log.error("Failed to delete temp file {}", tempFilePath, e);
            }
        }
    }

    private void saveOrMerge(DailyMetrics newMetrics) throws Exception {
        Optional<DailyMetrics> existingOpt = repository.findByDate(newMetrics.getDate());
        if (existingOpt.isPresent()) {
            DailyMetrics existing = existingOpt.get();

            // Merge Health
            if (newMetrics.getHealth() != null) {
                Health existingHealth = existing.getHealth();
                if (existingHealth == null) {
                    existing.setHealth(newMetrics.getHealth());
                } else {
                    Health newHealth = newMetrics.getHealth();
                    if (newHealth.getSleepHours() != null) existingHealth.setSleepHours(newHealth.getSleepHours());
                    if (newHealth.getRestingHeartRate() != null) existingHealth.setRestingHeartRate(newHealth.getRestingHeartRate());
                    if (newHealth.getBloodGlucoseFasting() != null) existingHealth.setBloodGlucoseFasting(newHealth.getBloodGlucoseFasting());
                    if (newHealth.getBloodGlucosePP() != null) existingHealth.setBloodGlucosePP(newHealth.getBloodGlucosePP());
                    if (newHealth.getWeight() != null) existingHealth.setWeight(newHealth.getWeight());
                }
            }

            // Merge Workout
            if (newMetrics.getWorkout() != null) {
                WorkoutLog existingWorkout = existing.getWorkout();
                if (existingWorkout == null) {
                    existing.setWorkout(newMetrics.getWorkout());
                } else {
                    List<Exercise> existingExercises = existingWorkout.getExercises();
                    List<Exercise> newExercises = newMetrics.getWorkout().getExercises();
                    if (newExercises != null) {
                        if (existingExercises == null) {
                            existingWorkout.setExercises(newExercises);
                        } else {
                            for (Exercise newEx : newExercises) {
                                boolean exists = existingExercises.stream().anyMatch(ex ->
                                        Objects.equals(ex.getName(), newEx.getName()) &&
                                        Objects.equals(ex.getDuration(), newEx.getDuration()));
                                if (!exists) {
                                    existingExercises.add(newEx);
                                }
                            }
                        }
                    }
                }
            }

            if (existing.getSource() == null || existing.getSource().equals("manual")) {
                existing.setSource("merged");
            } else if (!existing.getSource().equals("merged")) {
                existing.setSource("xml_import");
            }

            existing.setUpdatedAt(Instant.now());
            repository.save(existing);
        } else {
            repository.save(newMetrics);
        }
    }
}