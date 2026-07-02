package com.krypton.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krypton.dto.request.ProgramUploadRequest;
import com.krypton.dto.response.ProgramResponse;
import com.krypton.exception.ProgramNotFoundException;
import com.krypton.model.Checklist;
import com.krypton.model.DailyMetrics;
import com.krypton.model.Program;
import com.krypton.model.ProgramDay;
import com.krypton.model.WorkoutLog;
import com.krypton.repository.DailyMetricsRepository;
import com.krypton.repository.ProgramRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final DailyMetricsRepository dailyMetricsRepository;
    private final ObjectMapper objectMapper;

    public ProgramService(ProgramRepository programRepository,
                          DailyMetricsRepository dailyMetricsRepository,
                          ObjectMapper objectMapper) {
        this.programRepository = programRepository;
        this.dailyMetricsRepository = dailyMetricsRepository;
        this.objectMapper = objectMapper;
    }

    public ProgramResponse uploadProgram(ProgramUploadRequest request) throws ExecutionException, InterruptedException {
        List<ProgramDay> programDays = request.getDays() != null
                ? objectMapper.convertValue(request.getDays(), new TypeReference<List<ProgramDay>>() {})
                : List.of();

        Program program = Program.builder()
                .name(request.getName())
                .totalDays(programDays.size())
                .isActive(false)
                .currentDay(1)
                .days(programDays)
                .createdAt(Instant.now())
                .build();

        programRepository.save(program);
        return mapToResponse(program);
    }

    public void activateProgram(String id) throws ExecutionException, InterruptedException {
        // Retrieve the program to ensure it exists
        Program target = programRepository.findById(id)
                .orElseThrow(() -> new ProgramNotFoundException("No program exists with ID: " + id));

        List<Program> allPrograms = programRepository.findAll();
        for (Program p : allPrograms) {
            boolean shouldUpdate = false;
            if (p.getId().equals(id)) {
                if (!Boolean.TRUE.equals(p.getIsActive())) {
                    p.setIsActive(true);
                    shouldUpdate = true;
                }
            } else {
                if (Boolean.TRUE.equals(p.getIsActive())) {
                    p.setIsActive(false);
                    shouldUpdate = true;
                }
            }
            if (shouldUpdate) {
                programRepository.save(p);
            }
        }
    }

    public ProgramResponse getTodayWorkout() throws ExecutionException, InterruptedException {
        Program activeProgram = programRepository.findActiveProgram()
                .orElseThrow(() -> new ProgramNotFoundException("No active program found"));

        Integer currentDayNum = activeProgram.getCurrentDay();
        ProgramDay todayDay = activeProgram.getDays().stream()
                .filter(d -> d.getDay().equals(currentDayNum))
                .findFirst()
                .orElseThrow(() -> new ProgramNotFoundException("Workout day " + currentDayNum + " not found in program"));

        List<ProgramDay> filteredDays = List.of(todayDay);

        return ProgramResponse.builder()
                .id(activeProgram.getId())
                .name(activeProgram.getName())
                .totalDays(activeProgram.getTotalDays())
                .isActive(activeProgram.getIsActive())
                .currentDay(activeProgram.getCurrentDay())
                .days(objectMapper.convertValue(filteredDays, List.class))
                .build();
    }

    public void completeDay() throws ExecutionException, InterruptedException {
        Program activeProgram = programRepository.findActiveProgram()
                .orElseThrow(() -> new ProgramNotFoundException("No active program found"));

        Integer currentDayNum = activeProgram.getCurrentDay();
        ProgramDay todayDay = activeProgram.getDays().stream()
                .filter(d -> d.getDay().equals(currentDayNum))
                .findFirst()
                .orElseThrow(() -> new ProgramNotFoundException("Workout day " + currentDayNum + " not found in program"));

        String todayStr = LocalDate.now().toString();
        Optional<DailyMetrics> existingOpt = dailyMetricsRepository.findByDate(todayStr);
        DailyMetrics metrics;

        if (existingOpt.isPresent()) {
            metrics = existingOpt.get();
            Checklist checklist = metrics.getChecklist();
            if (checklist == null) {
                checklist = new Checklist();
            }
            checklist.setWorkout(true);
            metrics.setChecklist(checklist);

            metrics.setWorkout(WorkoutLog.builder()
                    .programName(activeProgram.getName())
                    .programDay(currentDayNum)
                    .title(todayDay.getTitle())
                    .exercises(todayDay.getExercises())
                    .build());
            metrics.setUpdatedAt(Instant.now());
        } else {
            metrics = DailyMetrics.builder()
                    .date(todayStr)
                    .checklist(Checklist.builder().workout(true).build())
                    .workout(WorkoutLog.builder()
                            .programName(activeProgram.getName())
                            .programDay(currentDayNum)
                            .title(todayDay.getTitle())
                            .exercises(todayDay.getExercises())
                            .build())
                    .source("manual")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        }

        dailyMetricsRepository.save(metrics);

        int nextDay = currentDayNum + 1;
        if (nextDay > activeProgram.getTotalDays()) {
            nextDay = 1;
        }
        activeProgram.setCurrentDay(nextDay);
        programRepository.save(activeProgram);
    }

    public List<ProgramResponse> listPrograms() throws ExecutionException, InterruptedException {
        return programRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ProgramResponse mapToResponse(Program program) {
        if (program == null) return null;
        List<Map<String, Object>> daysMap = null;
        if (program.getDays() != null) {
            daysMap = objectMapper.convertValue(program.getDays(), List.class);
        }
        return ProgramResponse.builder()
                .id(program.getId())
                .name(program.getName())
                .totalDays(program.getTotalDays())
                .isActive(program.getIsActive())
                .currentDay(program.getCurrentDay())
                .days(daysMap)
                .build();
    }
}