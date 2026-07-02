package com.krypton.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krypton.dto.request.DailyCheckInRequest;
import com.krypton.dto.response.TodaySummaryResponse;
import com.krypton.model.Checklist;
import com.krypton.model.DailyMetrics;
import com.krypton.model.Health;
import com.krypton.repository.DailyMetricsRepository;
import com.krypton.util.BenchmarkProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HealthService {

    private final DailyMetricsRepository repository;
    private final BenchmarkProvider benchmarkProvider;
    private final ObjectMapper objectMapper;

    public HealthService(DailyMetricsRepository repository, BenchmarkProvider benchmarkProvider, ObjectMapper objectMapper) {
        this.repository = repository;
        this.benchmarkProvider = benchmarkProvider;
        this.objectMapper = objectMapper;
    }

    public void submitDaily(DailyCheckInRequest request) throws ExecutionException, InterruptedException {
        String date = request.getDate();
        Optional<DailyMetrics> existingOpt = repository.findByDate(date);
        DailyMetrics metrics;

        if (existingOpt.isPresent()) {
            metrics = existingOpt.get();
            
            if (request.getChecklist() != null) {
                Checklist checklist = metrics.getChecklist();
                if (checklist == null) checklist = new Checklist();
                mergeChecklist(checklist, request.getChecklist());
                metrics.setChecklist(checklist);
            }
            
            if (request.getHealth() != null) {
                Health health = metrics.getHealth();
                if (health == null) health = new Health();
                mergeHealth(health, request.getHealth());
                metrics.setHealth(health);
            }
            
            if (request.getNotes() != null) {
                metrics.setNotes(request.getNotes());
            }
            metrics.setUpdatedAt(Instant.now());
        } else {
            Checklist checklist = null;
            if (request.getChecklist() != null) {
                checklist = new Checklist();
                mergeChecklist(checklist, request.getChecklist());
            }

            Health health = null;
            if (request.getHealth() != null) {
                health = new Health();
                mergeHealth(health, request.getHealth());
            }

            metrics = DailyMetrics.builder()
                    .date(date)
                    .checklist(checklist)
                    .health(health)
                    .notes(request.getNotes())
                    .source("manual")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        }

        repository.save(metrics);
    }

    public TodaySummaryResponse getTodaySummary(String dateStr) throws ExecutionException, InterruptedException {
        LocalDate targetDate = LocalDate.parse(dateStr);
        LocalDate thirtyDaysAgo = targetDate.minusDays(30);

        List<DailyMetrics> history = repository.findByDateRange(thirtyDaysAgo.toString(), dateStr);
        Map<String, DailyMetrics> historyMap = history.stream()
                .collect(Collectors.toMap(DailyMetrics::getDate, m -> m));

        DailyMetrics todayMetrics = historyMap.get(dateStr);
        
        Map<String, Boolean> checklistMap = new HashMap<>();
        if (todayMetrics != null && todayMetrics.getChecklist() != null) {
            Checklist c = todayMetrics.getChecklist();
            checklistMap.put("workout", c.getWorkout());
            checklistMap.put("eggs", c.getEggs());
            checklistMap.put("quinoa", c.getQuinoa());
            checklistMap.put("vegCurry", c.getVegCurry());
            checklistMap.put("soya", c.getSoya());
            checklistMap.put("water", c.getWater());
        }

        Map<String, Object> healthMap = new HashMap<>();
        Health todayHealth = todayMetrics != null ? todayMetrics.getHealth() : null;

        // Populate Sleep metrics
        healthMap.put("sleepHours", buildMetricDetail(
                todayHealth != null ? todayHealth.getSleepHours() : null,
                history, targetDate, Health::getSleepHours,
                benchmarkProvider.getSleepRange(), false
        ));

        // Populate Resting Heart Rate
        healthMap.put("restingHeartRate", buildMetricDetail(
                todayHealth != null ? todayHealth.getRestingHeartRate() : null,
                history, targetDate, Health::getRestingHeartRate,
                benchmarkProvider.getRestingHeartRateRange(), true
        ));

        // Populate Weight
        Double weightVal = todayHealth != null ? todayHealth.getWeight() : null;
        Map<String, Object> weightDetail = new HashMap<>();
        weightDetail.put("value", weightVal);
        if (weightVal != null) {
            double heightM = 1.78;
            double bmi = weightVal / (heightM * heightM);
            weightDetail.put("bmi", Math.round(bmi * 10.0) / 10.0);
        }
        BenchmarkProvider.IdealRange weightIdeal = benchmarkProvider.getWeightRange();
        weightDetail.put("ideal", Map.of("min", weightIdeal.min(), "max", weightIdeal.max(), "source", weightIdeal.source()));
        weightDetail.put("vsIdeal", getVsIdeal(weightVal, weightIdeal));
        healthMap.put("weight", weightDetail);

        // Populate Fasting Glucose
        Double fastingGlucose = todayHealth != null ? todayHealth.getBloodGlucoseFasting() : null;
        Map<String, Object> fastingDetail = new HashMap<>();
        fastingDetail.put("value", fastingGlucose);
        BenchmarkProvider.IdealRange fastingIdeal = benchmarkProvider.getFastingBloodGlucoseRange();
        fastingDetail.put("ideal", Map.of("min", fastingIdeal.min(), "max", fastingIdeal.max(), "source", fastingIdeal.source()));
        fastingDetail.put("vsIdeal", getVsIdeal(fastingGlucose, fastingIdeal));
        healthMap.put("bloodGlucoseFasting", fastingDetail);

        // Populate PP Glucose
        Double ppGlucose = todayHealth != null ? todayHealth.getBloodGlucosePP() : null;
        Map<String, Object> ppDetail = new HashMap<>();
        ppDetail.put("value", ppGlucose);
        BenchmarkProvider.IdealRange ppIdeal = benchmarkProvider.getPostPrandialBloodGlucoseRange();
        ppDetail.put("ideal", Map.of("max", ppIdeal.max(), "source", ppIdeal.source()));
        ppDetail.put("vsIdeal", getVsIdeal(ppGlucose, ppIdeal));
        healthMap.put("bloodGlucosePP", ppDetail);

        Map<String, Object> workoutResponseMap = null;
        if (todayMetrics != null && todayMetrics.getWorkout() != null) {
            workoutResponseMap = objectMapper.convertValue(todayMetrics.getWorkout(), Map.class);
        }

        return TodaySummaryResponse.builder()
                .date(dateStr)
                .age(benchmarkProvider.getAgeAt(targetDate))
                .checklist(checklistMap)
                .health(healthMap)
                .workout(workoutResponseMap)
                .build();
    }

    public List<TodaySummaryResponse> getHistory(String from, String to) throws ExecutionException, InterruptedException {
        List<DailyMetrics> metricsList = repository.findByDateRange(from, to);
        List<TodaySummaryResponse> responses = new ArrayList<>();
        for (DailyMetrics metrics : metricsList) {
            responses.add(getTodaySummary(metrics.getDate()));
        }
        return responses;
    }

    private Map<String, Object> buildMetricDetail(
            Double currentValue,
            List<DailyMetrics> history,
            LocalDate targetDate,
            Function<Health, Double> extractor,
            BenchmarkProvider.IdealRange idealRange,
            boolean lowerIsBetter) {

        Map<String, Object> detail = new HashMap<>();
        detail.put("value", currentValue);

        Double avg7d = getAverage(history, targetDate, 7, extractor);
        Double avg30d = getAverage(history, targetDate, 30, extractor);
        detail.put("avg7d", avg7d);
        detail.put("avg30d", avg30d);
        detail.put("trend", calculateTrend(avg7d, avg30d, lowerIsBetter));

        List<Double> sparkline = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = targetDate.minusDays(i);
            Optional<DailyMetrics> metricForDate = history.stream()
                    .filter(m -> m.getDate().equals(date.toString()))
                    .findFirst();
            Double val = null;
            if (metricForDate.isPresent() && metricForDate.get().getHealth() != null) {
                val = extractor.apply(metricForDate.get().getHealth());
            }
            sparkline.add(val != null ? val : 0.0);
        }
        detail.put("sparkline7d", sparkline);
        detail.put("ideal", Map.of("min", idealRange.min(), "max", idealRange.max(), "source", idealRange.source()));
        detail.put("vsIdeal", getVsIdeal(currentValue, idealRange));

        return detail;
    }

    private Double getAverage(List<DailyMetrics> history, LocalDate targetDate, int days, Function<Health, Double> extractor) {
        LocalDate cutoff = targetDate.minusDays(days);
        double sum = 0;
        int count = 0;
        for (DailyMetrics m : history) {
            LocalDate mDate = LocalDate.parse(m.getDate());
            if (!mDate.isBefore(cutoff) && !mDate.isAfter(targetDate) && m.getHealth() != null) {
                Double val = extractor.apply(m.getHealth());
                if (val != null) {
                    sum += val;
                    count++;
                }
            }
        }
        return count > 0 ? Math.round((sum / count) * 10.0) / 10.0 : null;
    }

    private String calculateTrend(Double avg7d, Double avg30d, boolean lowerIsBetter) {
        if (avg7d == null || avg30d == null) return "stable";
        double diff = avg7d - avg30d;
        if (Math.abs(diff) < 0.1) return "stable";
        if (diff > 0) {
            return lowerIsBetter ? "declining" : "improving";
        } else {
            return lowerIsBetter ? "improving" : "declining";
        }
    }

    private String getVsIdeal(Double val, BenchmarkProvider.IdealRange range) {
        if (val == null) return "no_data";
        if (range.min() != null && val < range.min()) return "below_range";
        if (range.max() != null && val > range.max()) return "above_range";
        return "in_range";
    }

    private void mergeChecklist(Checklist existing, Map<String, Boolean> updates) {
        if (updates.containsKey("workout")) existing.setWorkout(updates.get("workout"));
        if (updates.containsKey("eggs")) existing.setEggs(updates.get("eggs"));
        if (updates.containsKey("quinoa")) existing.setQuinoa(updates.get("quinoa"));
        if (updates.containsKey("vegCurry")) existing.setVegCurry(updates.get("vegCurry"));
        if (updates.containsKey("soya")) existing.setSoya(updates.get("soya"));
        if (updates.containsKey("water")) existing.setWater(updates.get("water"));
    }

    private void mergeHealth(Health existing, Map<String, Object> updates) {
        if (updates.containsKey("sleepHours")) existing.setSleepHours(toDouble(updates.get("sleepHours")));
        if (updates.containsKey("restingHeartRate")) existing.setRestingHeartRate(toDouble(updates.get("restingHeartRate")));
        if (updates.containsKey("bloodGlucoseFasting")) existing.setBloodGlucoseFasting(toDouble(updates.get("bloodGlucoseFasting")));
        if (updates.containsKey("bloodGlucosePP")) existing.setBloodGlucosePP(toDouble(updates.get("bloodGlucosePP")));
        if (updates.containsKey("weight")) existing.setWeight(toDouble(updates.get("weight")));
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}