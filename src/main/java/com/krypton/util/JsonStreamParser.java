package com.krypton.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.krypton.model.DailyMetrics;
import com.krypton.model.Exercise;
import com.krypton.model.Health;
import com.krypton.model.WorkoutLog;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

@Component
public class JsonStreamParser {

    private static final DateTimeFormatter FORMATTER_WITH_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final DateTimeFormatter FORMATTER_WITH_ZONE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public List<DailyMetrics> parseJson(Path filePath, Consumer<Integer> progressCallback) throws Exception {
        JsonFactory factory = new JsonFactory();
        Map<String, DailyAggregator> dailyMap = new HashMap<>();

        try (InputStream fileStream = Files.newInputStream(filePath);
             ProgressTrackingInputStream trackingStream = new ProgressTrackingInputStream(
                     fileStream, Files.size(filePath), progressCallback);
             JsonParser parser = factory.createParser(trackingStream)) {

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    if ("records".equals(name)) {
                        parser.nextToken(); // Move to START_ARRAY
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                if (parser.currentToken() == JsonToken.START_OBJECT) {
                                    Map<String, String> recordMap = readObjectFields(parser);
                                    String type = recordMap.get("type");
                                    if (type != null) {
                                        processRecord(type, recordMap, dailyMap);
                                    }
                                }
                            }
                        }
                    } else if ("workouts".equals(name)) {
                        parser.nextToken(); // Move to START_ARRAY
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                if (parser.currentToken() == JsonToken.START_OBJECT) {
                                    Map<String, String> workoutMap = readObjectFields(parser);
                                    processWorkout(workoutMap, dailyMap);
                                }
                            }
                        }
                    }
                }
            }
        }

        return convertToDailyMetrics(dailyMap);
    }

    private Map<String, String> readObjectFields(JsonParser parser) throws IOException {
        Map<String, String> fields = new HashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.currentName();
                parser.nextToken(); // Move to value
                fields.put(fieldName, parser.getText());
            }
        }
        return fields;
    }

    private void processRecord(String type, Map<String, String> record, Map<String, DailyAggregator> dailyMap) {
        if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type)) {
            String value = record.get("value");
            if (value != null && value.toLowerCase().contains("asleep")) {
                String startDateStr = record.get("startDate");
                String endDateStr = record.get("endDate");
                if (startDateStr != null && endDateStr != null) {
                    try {
                        ZonedDateTime start = parseDateTime(startDateStr);
                        ZonedDateTime end = parseDateTime(endDateStr);
                        if (start != null && end != null) {
                            double durationHours = Duration.between(start, end).toMillis() / (1000.0 * 60.0 * 60.0);
                            if (durationHours > 0) {
                                String dateKey = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                                agg.sleepHoursSum += durationHours;
                            }
                        }
                    } catch (Exception e) {
                        // Resilient parsing
                    }
                }
            }
        } else if ("HKQuantityTypeIdentifierBodyMass".equals(type)) {
            String valueStr = record.get("value");
            String startDateStr = record.get("startDate");
            if (valueStr != null && startDateStr != null) {
                try {
                    double val = Double.parseDouble(valueStr);
                    ZonedDateTime start = parseDateTime(startDateStr);
                    if (start != null) {
                        String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                        Instant recordTime = start.toInstant();
                        if (agg.latestWeightTime == null || recordTime.isAfter(agg.latestWeightTime)) {
                            agg.latestWeightTime = recordTime;
                            agg.latestWeightValue = val;
                        }
                    }
                } catch (Exception e) {
                    // Resilient parsing
                }
            }
        } else if ("HKQuantityTypeIdentifierBloodGlucose".equals(type)) {
            String valueStr = record.get("value");
            String startDateStr = record.get("startDate");
            if (valueStr != null && startDateStr != null) {
                try {
                    double val = Double.parseDouble(valueStr);
                    ZonedDateTime start = parseDateTime(startDateStr);
                    if (start != null) {
                        String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                        int hour = start.getHour();
                        if (hour < 10) {
                            agg.bloodGlucoseFastingSum += val;
                            agg.bloodGlucoseFastingCount++;
                        } else {
                            agg.bloodGlucosePPSum += val;
                            agg.bloodGlucosePPCount++;
                        }
                    }
                } catch (Exception e) {
                    // Resilient parsing
                }
            }
        } else if ("HKQuantityTypeIdentifierRestingHeartRate".equals(type)) {
            String valueStr = record.get("value");
            String startDateStr = record.get("startDate");
            if (valueStr != null && startDateStr != null) {
                try {
                    double val = Double.parseDouble(valueStr);
                    ZonedDateTime start = parseDateTime(startDateStr);
                    if (start != null) {
                        String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                        agg.restingHeartRateSum += val;
                        agg.restingHeartRateCount++;
                    }
                } catch (Exception e) {
                    // Resilient parsing
                }
            }
        }
    }

    private void processWorkout(Map<String, String> workout, Map<String, DailyAggregator> dailyMap) {
        String activityType = workout.get("workoutActivityType");
        String durationStr = workout.get("duration");
        String durationUnit = workout.get("durationUnit");
        String startDateStr = workout.get("startDate");
        String sourceName = workout.get("sourceName");

        if (activityType != null && startDateStr != null) {
            try {
                ZonedDateTime start = parseDateTime(startDateStr);
                if (start != null) {
                    String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());

                    String exerciseName = activityType.replace("HKWorkoutActivityType", "");
                    String durationVal = "0";
                    if (durationStr != null) {
                        try {
                            double d = Double.parseDouble(durationStr);
                            durationVal = String.format(Locale.US, "%.1f", d);
                        } catch (NumberFormatException e) {
                            durationVal = durationStr;
                        }
                    }

                    Exercise exercise = Exercise.builder()
                            .name(exerciseName)
                            .sets(1)
                            .duration(durationVal)
                            .unit(durationUnit != null ? durationUnit : "min")
                            .note("Imported workout from " + (sourceName != null ? sourceName : "Health"))
                            .build();

                    agg.exercises.add(exercise);
                }
            } catch (Exception e) {
                // Resilient parsing
            }
        }
    }

    private List<DailyMetrics> convertToDailyMetrics(Map<String, DailyAggregator> dailyMap) {
        List<DailyMetrics> list = new ArrayList<>();
        for (Map.Entry<String, DailyAggregator> entry : dailyMap.entrySet()) {
            String dateKey = entry.getKey();
            DailyAggregator agg = entry.getValue();

            Health health = Health.builder()
                    .sleepHours(agg.sleepHoursSum > 0 ? Math.round(agg.sleepHoursSum * 10.0) / 10.0 : null)
                    .restingHeartRate(agg.restingHeartRateCount > 0 ? Math.round((agg.restingHeartRateSum / agg.restingHeartRateCount) * 10.0) / 10.0 : null)
                    .bloodGlucoseFasting(agg.bloodGlucoseFastingCount > 0 ? Math.round((agg.bloodGlucoseFastingSum / agg.bloodGlucoseFastingCount) * 10.0) / 10.0 : null)
                    .bloodGlucosePP(agg.bloodGlucosePPCount > 0 ? Math.round((agg.bloodGlucosePPSum / agg.bloodGlucosePPCount) * 10.0) / 10.0 : null)
                    .weight(agg.latestWeightValue > 0 ? Math.round(agg.latestWeightValue * 10.0) / 10.0 : null)
                    .build();

            WorkoutLog workout = null;
            if (!agg.exercises.isEmpty()) {
                workout = WorkoutLog.builder()
                        .exercises(agg.exercises)
                        .build();
            }

            DailyMetrics dm = DailyMetrics.builder()
                    .date(dateKey)
                    .health(health)
                    .workout(workout)
                    .source("apple_health_import")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            list.add(dm);
        }
        return list;
    }

    private ZonedDateTime parseDateTime(String dateStr) {
        if (dateStr == null) return null;
        try {
            if (dateStr.contains("+") || dateStr.contains("-")) {
                if (dateStr.contains("T")) {
                    return ZonedDateTime.parse(dateStr, ISO_FORMATTER);
                }
                try {
                    return ZonedDateTime.parse(dateStr, FORMATTER_WITH_OFFSET);
                } catch (Exception e) {
                    return ZonedDateTime.parse(dateStr, FORMATTER_WITH_ZONE);
                }
            }
            LocalDateTime ldt = LocalDateTime.parse(dateStr);
            return ldt.atZone(ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private static class DailyAggregator {
        double sleepHoursSum = 0.0;
        double latestWeightValue = 0.0;
        Instant latestWeightTime = null;
        double restingHeartRateSum = 0.0;
        int restingHeartRateCount = 0;
        double bloodGlucoseFastingSum = 0.0;
        int bloodGlucoseFastingCount = 0;
        double bloodGlucosePPSum = 0.0;
        int bloodGlucosePPCount = 0;
        List<Exercise> exercises = new ArrayList<>();
    }

    private static class ProgressTrackingInputStream extends FilterInputStream {
        private final long totalBytes;
        private long bytesRead = 0;
        private final Consumer<Integer> progressCallback;
        private int lastProgress = -1;

        protected ProgressTrackingInputStream(InputStream in, long totalBytes, Consumer<Integer> progressCallback) {
            super(in);
            this.totalBytes = totalBytes;
            this.progressCallback = progressCallback;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                bytesRead++;
                updateProgress();
            }
            return b;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int n = super.read(b);
            if (n != -1) {
                bytesRead += n;
                updateProgress();
            }
            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n != -1) {
                bytesRead += n;
                updateProgress();
            }
            return n;
        }

        private void updateProgress() {
            if (totalBytes > 0) {
                int progress = (int) ((bytesRead * 100) / totalBytes);
                if (progress != lastProgress) {
                    lastProgress = progress;
                    progressCallback.accept(progress);
                }
            }
        }
    }
}
